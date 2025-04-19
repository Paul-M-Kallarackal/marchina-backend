package com.marchina.agent;

import com.marchina.model.AgentResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent responsible for generating Entity Relationship Diagrams (ERDs).
 */
@Component
public class ERDAgent {
    private static final Logger logger = LoggerFactory.getLogger(ERDAgent.class);
    private static final int MAX_RETRIES = 3;

    private final ChatLanguageModel chatModel;
    private final DiagramValidator diagramValidator;

    public ERDAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator) {
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
    }

    /**
     * Generates an ERD based on the given description.
     *
     * @param description The description of the ERD to generate
     * @return AgentResponse containing the generated ERD or error
     */
    public AgentResponse generateERD(String description) {
        try {
            logger.info("Generating ERD for description: {}", description);
            
            int retryCount = 0;
            String currentDescription = description;
            
            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate ERD", retryCount + 1, MAX_RETRIES);
                
                // Generate ERD using LLM
                String erdPrompt = String.format("""
                    Generate a Mermaid ERD diagram based on this description:
                    %s
                    
                    Follow these rules:
                    1. Use proper Mermaid ERD syntax
                    2. Include all necessary entities and relationships
                    3. Use appropriate cardinality notations
                    4. Include primary and foreign keys
                    5. Use clear and descriptive names
                    
                    Provide only the Mermaid code, nothing else.
                    """, currentDescription);
                
                String mermaidCode = chatModel.generate(erdPrompt);
                
                // Validate the generated ERD
                String validationResult = diagramValidator.validateERD(mermaidCode);
                if (validationResult.contains("valid")) {
                    return new AgentResponse(true, "ERD generated successfully", mermaidCode);
                }
                
                // If validation fails, improve the description and retry
                String improvementPrompt = String.format("""
                    Improve this ERD description based on validation feedback:
                    Original Description: %s
                    Generated ERD: %s
                    Validation Feedback: %s
                    
                    Provide an improved version of the description.
                    """, description, mermaidCode, validationResult);
                
                currentDescription = chatModel.generate(improvementPrompt);
                retryCount++;
            }
            
            return new AgentResponse(false, 
                "Failed to generate valid ERD after " + MAX_RETRIES + " attempts");
                
        } catch (Exception e) {
            logger.error("Error generating ERD: {}", e.getMessage(), e);
            return new AgentResponse(false, "Error generating ERD: " + e.getMessage());
        }
    }

    public String explainERD(String mermaidCode) {
        String prompt = String.format("""
            Explain the following Mermaid ERD code in simple terms:
            %s
            
            Provide a clear explanation of the entities, their attributes, and relationships.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }

    public String generateSQL(String mermaidCode) {
        String prompt = String.format("""
            Generate SQL CREATE TABLE statements for the following Mermaid ERD:
            %s
            
            Please provide only the SQL statements without any additional text or explanation.
            Include primary keys, foreign keys, and appropriate data types.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }
} 