package com.marchina.agent;

import com.marchina.model.AgentResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlowchartAgent {
    private static final Logger logger = LoggerFactory.getLogger(FlowchartAgent.class);
    private static final int MAX_RETRIES = 3;

    private final ChatLanguageModel chatModel;
    private final DiagramValidator diagramValidator;

    public FlowchartAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator) {
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
    }

    public AgentResponse generateFlowchart(String description) {
        try {
            logger.info("Generating flowchart for description: {}", description);
            
            int retryCount = 0;
            String currentDescription = description;
            
            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate flowchart", retryCount + 1, MAX_RETRIES);
                
                // Generate flowchart using LLM
                String flowchartPrompt = String.format("""
                    Generate a Mermaid flowchart based on this description:
                    %s
                    
                    Follow these rules:
                    1. Use proper Mermaid flowchart syntax
                    2. Include all necessary steps and decision points
                    3. Use clear directional flow
                    4. Add appropriate labels and descriptions
                    5. Keep it clear and readable
                    
                    Provide only the Mermaid code, nothing else.
                    """, currentDescription);
                
                String mermaidCode = chatModel.generate(flowchartPrompt);
                
                // Validate the generated flowchart
                String validationResult = diagramValidator.validateFlowChart(mermaidCode);
                if (validationResult.contains("valid")) {
                    return new AgentResponse(true, "Flowchart generated successfully", mermaidCode);
                }
                
                // If validation fails, improve the description and retry
                String improvementPrompt = String.format("""
                    Improve this flowchart description based on validation feedback:
                    Original Description: %s
                    Generated Flowchart: %s
                    Validation Feedback: %s
                    
                    Provide an improved version of the description.
                    """, description, mermaidCode, validationResult);
                
                currentDescription = chatModel.generate(improvementPrompt);
                retryCount++;
            }
            
            return new AgentResponse(false, 
                "Failed to generate valid flowchart after " + MAX_RETRIES + " attempts");
                
        } catch (Exception e) {
            logger.error("Error generating flowchart: {}", e.getMessage(), e);
            return new AgentResponse(false, "Error generating flowchart: " + e.getMessage());
        }
    }

    public String explainFlowChart(String mermaidCode) {
        String prompt = String.format("""
            Explain the following Mermaid flowchart code in simple terms:
            %s
            
            Requirements:
            1. Start with an overview of the process
            2. Explain each decision point and its outcomes
            3. Describe the flow from start to end
            4. Highlight any important conditions or branches
            
            Provide a clear and concise explanation.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }
} 