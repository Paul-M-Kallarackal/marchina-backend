package com.marchina.agent;

import com.marchina.model.AgentResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Main agent that intelligently coordinates between specialized agents.
 */
@Component
public class MainAgent {
    private static final Logger logger = LoggerFactory.getLogger(MainAgent.class);
    private static final int MAX_RETRIES = 3;

    private final ChatLanguageModel chatModel;
    private final ERDAgent erdAgent;
    private final SQLGeneratorAgent sqlGeneratorAgent;
    private final FlowchartAgent flowchartAgent;
    private final DiagramValidator diagramValidator;

    public MainAgent(ChatLanguageModel chatModel,
                    ERDAgent erdAgent,
                    SQLGeneratorAgent sqlGeneratorAgent,
                    FlowchartAgent flowchartAgent,
                    DiagramValidator diagramValidator) {
        this.chatModel = chatModel;
        this.erdAgent = erdAgent;
        this.sqlGeneratorAgent = sqlGeneratorAgent;
        this.flowchartAgent = flowchartAgent;
        this.diagramValidator = diagramValidator;
    }

    /**
     * Processes a user request by intelligently mapping it to the appropriate agent.
     *
     * @param request The user's request
     * @return AgentResponse containing the result or error
     */
    public AgentResponse processRequest(String request) {
        try {
            logger.info("Processing request: {}", request);

            // Use LLM to determine the appropriate agent and action
            String analysisPrompt = String.format("""
                Analyze this request and determine:
                1. Which agent should handle it (ERD, SQL, or Flowchart)
                2. What specific action is needed (generate, validate, etc.)
                
                Request: %s
                
                Respond in the format: AGENT|ACTION
                Where AGENT is one of: ERD, SQL, FLOWCHART
                And ACTION is one of: GENERATE, VALIDATE
                """, request);

            String llmResponse = chatModel.generate(analysisPrompt);
            String[] parts = llmResponse.split("\\|");
            if (parts.length != 2) {
                return new AgentResponse(false, "Could not determine appropriate agent and action");
            }

            String agentType = parts[0].trim().toUpperCase();
            String action = parts[1].trim().toUpperCase();

            // Delegate to appropriate agent with retry logic
            return delegateToAgent(agentType, action, request);

        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            return new AgentResponse(false, "Error processing request: " + e.getMessage());
        }
    }

    /**
     * Delegates the request to the appropriate agent with retry logic.
     */
    private AgentResponse delegateToAgent(String agentType, String action, String request) {
        int retryCount = 0;
        AgentResponse result = null;
        String currentRequest = request;

        while (retryCount < MAX_RETRIES) {
            logger.info("Attempt {} of {} for {} agent", retryCount + 1, MAX_RETRIES, agentType);

            // Generate or validate based on action
            if ("GENERATE".equals(action)) {
                result = generateWithAgent(agentType, currentRequest);
            } else if ("VALIDATE".equals(action)) {
                result = validateWithAgent(agentType, currentRequest);
            } else {
                return new AgentResponse(false, "Unsupported action: " + action);
            }

            if (result.isSuccess()) {
                return result;
            }

            // If it's a generation request, validate the result
            if ("GENERATE".equals(action)) {
                String validationResult = validateResult(agentType, result.getMessage());
                if (validationResult.contains("valid")) {
                    return result;
                }

                // If validation fails, improve the request and retry
                String improvementPrompt = String.format("""
                    Improve this request based on validation feedback:
                    Original Request: %s
                    Generated Result: %s
                    Validation Feedback: %s
                    
                    Provide an improved version of the request.
                    """, request, result.getMessage(), validationResult);

                currentRequest = chatModel.generate(improvementPrompt);
                retryCount++;
            } else {
                return result;
            }
        }

        return new AgentResponse(false, 
            "Failed to generate valid result after " + MAX_RETRIES + " attempts");
    }

    /**
     * Generates content using the appropriate agent.
     */
    private AgentResponse generateWithAgent(String agentType, String request) {
        return switch (agentType) {
            case "ERD" -> erdAgent.generateERD(request);
            case "SQL" -> sqlGeneratorAgent.generateSQL(request);
            case "FLOWCHART" -> flowchartAgent.generateFlowchart(request);
            default -> new AgentResponse(false, "Unsupported agent type: " + agentType);
        };
    }

    /**
     * Validates content using the appropriate validator.
     */
    private AgentResponse validateWithAgent(String agentType, String content) {
        String validationResult = validateResult(agentType, content);
        return new AgentResponse(true, validationResult);
    }

    /**
     * Validates the result based on agent type.
     */
    private String validateResult(String agentType, String content) {
        return switch (agentType) {
            case "ERD" -> diagramValidator.validateERD(content);
            case "FLOWCHART" -> diagramValidator.validateFlowChart(content);
            case "SQL" -> diagramValidator.validateMermaidSyntax(content);
            default -> "Unsupported agent type for validation";
        };
    }
} 