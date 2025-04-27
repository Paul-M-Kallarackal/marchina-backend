package com.marchina.agent;
import com.marchina.model.Project;
import com.marchina.model.ChatResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
// Add these imports at the top
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.marchina.config.JwtConfig.JwtService;
import com.marchina.controller.ProjectController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.HashMap;


@Service
public class RequirementExtractorVoice {
    private static final Logger logger = LoggerFactory.getLogger(RequirementExtractorVoice.class);
    private final ChatLanguageModel chatModel;
    private final TTSAgent ttsAgent;
    private final MainAgent mainAgent;
    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final ProjectController projectController;

    // Use ConcurrentMap for session state management
    private final ConcurrentMap<String, ConversationState> userSessions = new ConcurrentHashMap<>();

    // Removed conflicting instance variables

    // Inner class to hold conversation state per user
    public static class ConversationState {
        List<String> conversationHistory = new ArrayList<>();
        boolean requirementsGathered = false;
        String projectName;
        String projectDescription;
        Project project; // Changed from Long projectId to Project project
        String currentToken;
    }


    public RequirementExtractorVoice(
            ChatLanguageModel chatModel, 
            TTSAgent ttsAgent, 
            MainAgent mainAgent,
            JdbcTemplate jdbcTemplate,
            JwtService jwtService,
            ProjectController projectController) {
        this.chatModel = chatModel;
        this.ttsAgent = ttsAgent;
        this.mainAgent = mainAgent;
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.projectController = projectController;
    }

    public ChatResponse processMessage(String userMessage, String authHeader) {
        String userId = null; // Initialize userId
        ConversationState state = null; // Initialize state
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            userId = claims.get("userId").toString(); // Assign userId

            // Get or create conversation state for this user
            state = userSessions.computeIfAbsent(userId, k -> new ConversationState()); // Assign state

            state.currentToken = authHeader;
            state.conversationHistory.add("User: " + userMessage);
            
            String prompt;
            String aiResponse;
            
            if (state.projectName == null) {
                // First analyze the user message to extract project name
                prompt = String.format("""
                    Analyze this message and extract a suitable project name:
                    User Message: %s
                    
                    Requirements:
                    1. Name should be concise and descriptive
                    2. Use standard naming conventions
                    3. Avoid special characters
                    4. Maximum 50 characters
                    5. If the user message contains a Project name, extract it.
                    6. If the user message doesn't contain a project name, suggest a suitable one.
                    
                    Return only the project name, nothing else.
                    """, userMessage);
                    
                state.projectName = chatModel.generate(prompt);
                
                // Then generate engaging response
                prompt = String.format("""
                    You are an AI assistant helping gather project requirements.
                    Project Name: %s
                    Last Message: %s
                    
                    Generate a response that:
                    1. Acknowledges the project name
                    2. Asks about the core functionality
                    3. Encourages detailed explanation
                    4. Maintains conversational tone
                    5. Is short and engaging, and only asks one question at a time.
                    6. Doesn't use any markdown formatting
                    
                    Provide only the response text.
                    """, state.projectName, userMessage);
                    
                aiResponse = chatModel.generate(prompt);
                
            } else if (!state.requirementsGathered) {
                // Check if we have enough information to generate requirements
                String fullConversation = String.join("\n", state.conversationHistory);
                
                prompt = String.format("""
                    Based on this conversation about project "%s", determine if we have enough information to generate requirements:
                    
                    %s
                    
                    Analyze the conversation and determine:
                    1. Is there enough detail to generate a technical diagrams?
                    2. If user instructs to generate diagrams in the last message.
                    
                    Return only "SUFFICIENT" or "INSUFFICIENT" followed by a brief reason.
                    """, state.projectName, fullConversation);
                
                String assessmentResult = chatModel.generate(prompt);
                
                if (assessmentResult.startsWith("SUFFICIENT")) {
                    // We have enough information, generate the project description
                    prompt = String.format("""
                        Based on this conversation about project "%s":
                        
                        %s
                        
                        Generate a comprehensive project description that:
                        1. Summarizes the project purpose
                        2. Lists all key features and requirements
                        3. Includes any technical constraints mentioned by the user.
                        4. Is structured and detailed enough for technical diagram generation
                        5. Doesn't use any markdown formatting.
                        6. Contains three lines maximum.
                        
                        Provide only the description text.
                        """, state.projectName, fullConversation);
                    
                    state.projectDescription = chatModel.generate(prompt);
                    state.requirementsGathered = true;
                    
                    // Create the project, stores Project object in state.project
                    createProject(state);
                    
                    // Generate the single optimal diagram
                    generateOptimalDiagram(state);
                    
                    prompt = String.format("""
                        Based on the gathered information:
                        Project Name: %s
                        Project Description: %s
                        
                        Generate a confirmation message that:
                        1. Summarizes the understood requirements
                        2. Confirms proceeding to diagram generation
                        3. Sets expectations for next steps
                        4. Doesn't use any markdown formatting.
                        5. Keep it short, concise, and precise.
                        
                        Provide only the response text.
                        """, state.projectName, state.projectDescription);
                    
                    aiResponse = chatModel.generate(prompt);
                } else {
                    // We need more information
                    prompt = String.format("""
                        You are an AI assistant helping gather project requirements.
                        Project Name: %s
                        Conversation so far: %s
                        
                        Generate a response that:
                        1. Acknowledges the information provided so far
                        2. Asks specific questions to gather missing details
                        3. Guides the user toward providing complete requirements
                        4. Maintains conversational tone
                        5. Is short and engaging
                        6. Doesn't use any markdown formatting.
                        
                        Provide only the response text.
                        """, state.projectName, fullConversation);
                    
                    aiResponse = chatModel.generate(prompt);
                }
            } else {
                aiResponse = "Perfect! I've created your project and generated the technical diagrams. You can view them now.";
            }
            
            state.conversationHistory.add("AI: " + aiResponse);
            
            String audioData = ttsAgent.generateSpeech(aiResponse);
            
            // Use state object for the response
            return new ChatResponse(aiResponse, audioData, state.requirementsGathered, getProjectId(state)); 

        } catch (Exception e) {
            logger.error("Error processing message for user {}: {}", userId, e.getMessage(), e);
            // Handle error, potentially returning an error response
            String errorMsg = "Sorry, I encountered an error processing your request.";
            String errorAudio = ""; // Avoid TTS call on error potentially
            try {
                 errorAudio = ttsAgent.generateSpeech(errorMsg);
            } catch (Exception ttsEx) {
                 logger.error("TTS agent failed during error handling: {}", ttsEx.getMessage());
            }
             // Return error response using state if available, otherwise use defaults
             boolean reqGathered = (state != null) ? state.requirementsGathered : false;
             Long projId = (state != null) ? getProjectId(state) : null;
             return new ChatResponse(errorMsg, errorAudio, reqGathered, projId);
            // Or rethrow: throw new RuntimeException("Failed to process message", e);
        }
    }
    
    // Accepts ConversationState, stores Project in state.project
    private void createProject(ConversationState state) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("name", state.projectName);
            payload.put("description", state.projectDescription);

            ResponseEntity<?> response = projectController.createProject(payload, state.currentToken);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() instanceof Map) {
                @SuppressWarnings("unchecked") // Suppress warning for cast known to be safe here
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                // Assuming 'project' key holds a Project object (or compatible map)
                Object projectData = responseBody.get("project");
                
                if (projectData instanceof Project) {
                     state.project = (Project) projectData;
                } else if (projectData instanceof Map) {
                     // Handle if ProjectController returns a Map instead of Project object
                     @SuppressWarnings("unchecked")
                     Map<String, Object> projectMap = (Map<String, Object>) projectData;
                     state.project = new Project();
                     state.project.setId((Long) projectMap.get("id"));
                     state.project.setUserId((Long) projectMap.get("user_id")); 
                     state.project.setName((String) projectMap.get("name"));
                     state.project.setDescription((String) projectMap.get("description"));
                     // Add other fields if necessary
                } else {
                    throw new RuntimeException("Unexpected project data type in response from ProjectController: " + (projectData != null ? projectData.getClass().getName() : "null"));
                }

                if (state.project != null && state.project.getId() != null) {
                    logger.info("Voice flow created project with ID: {}", state.project.getId());
                } else {
                    throw new RuntimeException("Project object or ID was null after processing response from ProjectController");
                }
            } else {
                String errorBody = response.hasBody() ? response.getBody().toString() : "No error body";
                logger.error("Failed to create project through ProjectController. Status: {}, Body: {}", response.getStatusCode(), errorBody);
                throw new RuntimeException("Failed to create project through ProjectController. Status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error creating project: {}", e.getMessage(), e);
            throw new RuntimeException("Error during project creation: " + e.getMessage(), e);
        }
    }

    // Accepts ConversationState, uses state.project and state.projectDescription
    private void generateOptimalDiagram(ConversationState state) {
        if (state.project == null) {
            logger.error("Cannot generate diagram because project object is null in state.");
            return; 
        }
        if (state.projectDescription == null || state.projectDescription.trim().isEmpty()) {
             logger.warn("Project description is empty for project {}, skipping diagram generation.", state.project.getId());
             return;
        }

        try {
            // Determine the optimal diagram type using LLM based on projectDescription
             String analysisPrompt = String.format("""
                Analyze this project description and determine the single most appropriate diagram type to visualize it.
                Available types: ERD, Flowchart, Sequence Diagram, Class Diagram.
                Consider the focus of the description (data structure, process flow, interactions, object structure).
                
                Description:
                %s
                
                Respond ONLY with the name of the single most appropriate diagram type (e.g., Flowchart, ERD, Sequence Diagram, Class Diagram).
                """, state.projectDescription);

            String optimalDiagramType = chatModel.generate(analysisPrompt).trim();
            // Basic validation/fallback
            List<String> validTypes = List.of("ERD", "Flowchart", "Sequence Diagram", "Class Diagram");
            if (!validTypes.contains(optimalDiagramType)) {
                logger.warn("LLM returned invalid diagram type '{}' based on voice description. Defaulting to Flowchart.", optimalDiagramType);
                optimalDiagramType = "Flowchart"; // Fallback to Flowchart
            }

            logger.info("Determined optimal diagram type for project {} from voice context: {}", state.project.getId(), optimalDiagramType);
            
            // Call MainAgent to generate the determined optimal diagram type using projectDescription
            mainAgent.processRequest(state.project, optimalDiagramType, state.projectDescription);
            logger.info("Finished attempt to generate optimal diagram ({}) for project {}", optimalDiagramType, state.project.getId());

        } catch (Exception e) {
            logger.error("Error determining or generating optimal diagram for project {} from voice context: {}", 
                         (state.project != null ? state.project.getId() : "null"), e.getMessage(), e);
            // Logged the error, not throwing
        }
    }

    // Getters now accept state
    public boolean isRequirementsGathered(ConversationState state) {
        return state.requirementsGathered;
    }

    public String getProjectName(ConversationState state) {
        return state.projectName;
    }

    public String getProjectDescription(ConversationState state) {
        return state.projectDescription;
    }
    
    public Long getProjectId(ConversationState state) {
        // Retrieve ID from the project object within the state
        return (state != null && state.project != null) ? state.project.getId() : null;
    }

    // Optional: Method to retrieve state might be useful externally or for testing
    public ConversationState getUserState(String userId) {
        return userSessions.computeIfAbsent(userId, k -> new ConversationState());
    }
}
