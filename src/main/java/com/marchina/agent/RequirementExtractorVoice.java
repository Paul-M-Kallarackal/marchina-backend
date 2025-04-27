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

    
    private String projectName;
    private List<String> conversationHistory = new ArrayList<>();
    private Map<String, ConversationState> userSessions = new ConcurrentHashMap<>();
    // private boolean requirementsGathered = false;
    // private String projectDescription;
    // private Long projectId;

    // private String currentToken;

    // public ChatResponse processMessage(String userMessage, String authHeader) {
        
    //     // Rest of the existing processMessage logic
    // }

    // private String getCurrentUserToken() {
    //     return this.currentToken;
    // }

    // Create a class to hold conversation state
    public static class ConversationState {
        List<String> conversationHistory = new ArrayList<>();
        boolean requirementsGathered = false;
        String projectName;
        String projectDescription;
        Long projectId;
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

    public ChatResponse processMessage(String userMessage,String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            String userId = claims.get("userId").toString();

            // Get or create conversation state for this user
            ConversationState state = userSessions.computeIfAbsent(userId, k -> new ConversationState());


            state.currentToken = authHeader;
            // Add user message to conversation history
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
                        
                        Provide only the description text.
                        """, state.projectName, fullConversation);
                    
                    state.projectDescription = chatModel.generate(prompt);
                    state.requirementsGathered = true;
                    
                    // Create the project in the database
                    createProject(state);
                    
                    // Generate diagrams using MainAgent
                    generateDiagrams(state);
                    
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
            
            // Add AI response to conversation history
            state.conversationHistory.add("AI: " + aiResponse);
            
            String audioData = ttsAgent.generateSpeech(aiResponse);
            return new ChatResponse(aiResponse, audioData, state.requirementsGathered, state.projectId);
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage());
            throw new RuntimeException("Failed to process message", e);
        }
    }
    
    private void createProject(ConversationState state) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("name", state.projectName);
            payload.put("description", state.projectDescription);

            ResponseEntity<?> response = projectController.createProject(payload, state.currentToken);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                Project project = (Project) responseBody.get("project");
                state.projectId = project.getId();
                logger.info("Voice flow created project with ID: {}", state.projectId);
            } else {
                throw new RuntimeException("Failed to create project through ProjectController");
            }

        } catch (Exception e) {
            logger.error("Error creating project: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create project", e);
        }
    }


    private void generateDiagrams(ConversationState state) {
        if (state.projectId == null) {
            throw new IllegalStateException("Project ID is null, cannot generate diagrams");
        }

        try {
            // Ask the model what diagrams would be most useful
            String analysisPrompt = String.format("""
                Analyze this project description and determine which diagrams would be most useful:
                
                Project Description: %s
                
                Available diagram types:
                - ERD (for data relationships)
                - Flowchart (for process flows)
                - Sequence Diagram (for interaction sequences)
                - Class Diagram (for object relationships)
                
                Return only one diagram type that is needed, lowercase.
                """, state.projectDescription);

            logger.debug("Analyzing project for diagram types needed");
            String diagramTypes = chatModel.generate(analysisPrompt);
            
            // Process each recommended diagram type
            for (String diagramType : diagramTypes.split("\n")) {
                diagramType = diagramType.trim().toLowerCase();
                if (!diagramType.isEmpty()) {
                    logger.debug("Generating {} for project {}", diagramType, state.projectId);
                    mainAgent.processRequest(state.projectId, diagramType, state.projectDescription);
                }
            }

            logger.info("Generated recommended diagrams for project {}", state.projectId);

        } catch (Exception e) {
            logger.error("Error generating diagrams for project {}: {}", state.projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate diagrams", e);
        }
    }

    
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
        return state.projectId;
    }

    public ConversationState getUserState(String userId) {
    return userSessions.computeIfAbsent(userId, k -> new ConversationState());
}


}
