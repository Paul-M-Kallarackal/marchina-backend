package com.marchina.agent;

import com.marchina.model.AgentResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import com.marchina.model.Project;
import com.marchina.model.Diagram;

import java.time.Duration;
import java.util.List;
import java.util.Map; // Needed for string-based processRequest
import java.util.Optional; // Import Optional

/**
 * Main agent that intelligently coordinates between specialized agents.
 */
@Component
public class MainAgent {
    private static final Logger logger = LoggerFactory.getLogger(MainAgent.class);
    private static final int MAX_RETRIES = 3;

    private final ChatLanguageModel chatModel;
    private final ERDAgent erdAgent;
    private final FlowchartAgent flowchartAgent;
    private final ClassDiagramAgent classDiagramAgent;
    private final SequenceDiagramAgent sequenceDiagramAgent;
    private final DiagramValidator diagramValidator;
    private final JdbcTemplate jdbcTemplate;
    // Needs ObjectMapper if we implement validation retries with JSON parsing here
    // private final ObjectMapper objectMapper; 


    // Ensure ObjectMapper is included if needed, esp. if restoring delegateToAgent logic
    public MainAgent(ChatLanguageModel chatModel,
                    ERDAgent erdAgent,
                    FlowchartAgent flowchartAgent,
                    ClassDiagramAgent classDiagramAgent,
                    SequenceDiagramAgent sequenceDiagramAgent,
                    DiagramValidator diagramValidator,
                    JdbcTemplate jdbcTemplate /*, ObjectMapper objectMapper */ ) {
        this.chatModel = chatModel;
        this.erdAgent = erdAgent;
        this.flowchartAgent = flowchartAgent;
        this.classDiagramAgent = classDiagramAgent;
        this.sequenceDiagramAgent = sequenceDiagramAgent;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
        // this.objectMapper = objectMapper;
        logger.info("MainAgent initialized");
    }

    /**
     * Processes a user request by intelligently mapping it to the appropriate agent.
     * This version takes a general string request and uses an LLM to determine the agent/action.
     *
     * @param request The user's general request string
     * @return AgentResponse containing the result or error
     */
    public AgentResponse processRequest(String request) {
        try {
            logger.info("Processing general agent request: {}", request);

            // Use LLM to determine the appropriate agent and action
            String analysisPrompt = String.format("""
                Analyze this request and determine:
                1. Which agent should handle it (ERD, FLOWCHART, SEQUENCE, CLASS, or OTHER)
                2. What specific action is needed (e.g., GENERATE, VALIDATE, EXPLAIN, OTHER)
                
                Request: %s
                
                Respond ONLY in the format: AGENT|ACTION
                Example: FLOWCHART|GENERATE
                """, request);
            // Note: Added SEQUENCE, CLASS, OTHER options based on available agents.
            // The original only had ERD, SQL, FLOWCHART.

            String llmResponse = chatModel.generate(analysisPrompt);
            String[] parts = llmResponse.split("\\|");
            if (parts.length != 2) {
                 logger.warn("Could not determine agent/action from LLM response: {}", llmResponse);
                // Default or error handling needed here - perhaps pass to a general conversational agent?
                 return new AgentResponse(false, "Could not determine appropriate agent and action from request.");
            }

            String agentType = parts[0].trim().toUpperCase();
            String action = parts[1].trim().toUpperCase();
            logger.info("Determined agent: {}, action: {}", agentType, action);

            // Delegate to appropriate agent based on analysis
            // This part needs refinement based on what actions each agent supports beyond generate/save
            // For now, let's handle basic generation calls if requested.
            // The original delegateToAgent logic with retries might be useful here too.
            switch (agentType) {
                case "ERD":
                    if ("GENERATE".equals(action)) {
                        // This agent now expects Project context, cannot be called with just request string
                        // return erdAgent.generateERD(request); // Old call - incompatible
                         return new AgentResponse(false, "ERD generation requires project context. Please use the project creation endpoint.");
                    } else if ("EXPLAIN".equals(action)) {
                         // Assuming 'request' contains the mermaid code to explain
                         String explanation = erdAgent.explainERD(request);
                         return new AgentResponse(true, explanation);
                    } // Add validation etc. if needed
                    break;
                case "FLOWCHART":
                    if ("GENERATE".equals(action)) {
                        // return flowchartAgent.generateFlowchart(request); // Old call - incompatible
                         return new AgentResponse(false, "Flowchart generation requires project context. Please use the project creation endpoint.");
                    } else if ("EXPLAIN".equals(action)) {
                         String explanation = flowchartAgent.explainFlowChart(request);
                         return new AgentResponse(true, explanation);
                    }
                    break;
                case "SEQUENCE": // Added Sequence
                    if ("GENERATE".equals(action)) {
                        // return sequenceDiagramAgent.generateSequenceDiagram(request); // Old call - incompatible
                        return new AgentResponse(false, "Sequence diagram generation requires project context. Please use the project creation endpoint.");
                    } else if ("EXPLAIN".equals(action)) {
                         String explanation = sequenceDiagramAgent.explainSequenceDiagram(request);
                         return new AgentResponse(true, explanation);
                    }
                    break;
                case "CLASS": // Added Class
                    if ("GENERATE".equals(action)) {
                         // return classDiagramAgent.generateClassDiagram(request); // Old call - incompatible
                         return new AgentResponse(false, "Class diagram generation requires project context. Please use the project creation endpoint.");
                    } else if ("EXPLAIN".equals(action)) {
                         String explanation = classDiagramAgent.explainClassDiagram(request);
                         return new AgentResponse(true, explanation);
                    }
                    break;
                // Add cases for VALIDATE action if needed, calling diagramValidator
                default:
                     logger.warn("Unhandled agent type or action: {} | {}", agentType, action);
                    // Pass to a general LLM if no specific agent matches?
                    // return new AgentResponse(true, chatModel.generate(request)); // Example fallback
                     return new AgentResponse(false, "Unsupported agent type or action determined: " + agentType + "|" + action);
            }
             return new AgentResponse(false, "Action '" + action + "' not fully implemented for agent '" + agentType + "' in this context.");

        } catch (Exception e) {
            logger.error("Error processing general agent request: {}", e.getMessage(), e);
            return new AgentResponse(false, "Error processing request: " + e.getMessage());
        }
    }

    /**
     * Processes a request to generate and save a specific diagram type for a project.
     * Accepts Project object for context.
     * @return An Optional containing the created Diagram object, or empty if generation failed.
     */
    public Optional<Diagram> processRequest(Project project, String diagramType, String requirements) {
        try {
            Long projectId = project.getId();
            logger.info("Processing {} request for project {} and waiting for result...", diagramType, projectId);

            Optional<Diagram> createdDiagram = Optional.empty(); // Initialize

            // Route to appropriate agent, capture the result
            switch (diagramType.toLowerCase()) {
                case "erd":
                case "entity relationship diagram":
                    createdDiagram = erdAgent.generateAndSaveERD(project, requirements); 
                    break;

                case "flowchart":
                case "flow chart":
                    createdDiagram = flowchartAgent.generateAndSaveFlowchart(project, requirements); 
                    break;

                case "sequence diagram":
                    createdDiagram = sequenceDiagramAgent.generateAndSaveSequenceDiagram(project, requirements); 
                    break;

                case "class diagram":
                    createdDiagram = classDiagramAgent.generateAndSaveClassDiagram(project, requirements); 
                    break;

                default:
                    logger.error("Unsupported diagram type requested: {}", diagramType);
                    throw new IllegalArgumentException("Unsupported diagram type: " + diagramType);
            }

            if (createdDiagram.isPresent()) {
                 logger.info("Successfully processed and created {} (ID: {}) for project {}", 
                           diagramType, createdDiagram.get().getId(), projectId);
            } else {
                 logger.warn("Processing completed for {}, but diagram object was not returned for project {}", 
                           diagramType, projectId);
            }
            return createdDiagram; // Return the Optional<Diagram>

        } catch (Exception e) {
            logger.error("Error processing {} request for project {}: {}", diagramType, project.getId(), e.getMessage(), e);
            // Throwing runtime exception propagates failure, caller should handle
            // Alternatively, could return Optional.empty() here if caller should proceed
            throw new RuntimeException("Failed to process diagram request for type " + diagramType, e);
            // return Optional.empty();
        }
    }

    private String generateDiagramName(String diagramType) {
        return diagramType.replaceAll("\\s+", "_").toLowerCase();
    }
} 