package com.marchina.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequirementExtractorAgent {
    private static final Logger logger = LoggerFactory.getLogger(RequirementExtractorAgent.class);
    private final ChatLanguageModel chatModel;

    @Autowired
    public RequirementExtractorAgent(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
        logger.info("RequirementExtractorAgent initialized");
    }

    public String extractDetailedRequirements(String name, String description) {
        try {
            logger.info("Extracting detailed requirements for project: {}", name);

            String prompt = String.format("""
                Analyze the following project details:
                Name: %s
                Description: %s

                Generate a single, consolidated, and highly detailed set of functional and technical requirements suitable for creating a technical diagram (like a flowchart).
                Include:
                - Core purpose and goals.
                - Key actors/users and their interactions.
                - Step-by-step process flows, including decisions, inputs, outputs, and loops.
                - Data entities involved and their key attributes/relationships (if applicable).
                - System components or classes involved (if applicable).
                - Any specific constraints or non-functional requirements mentioned.
                
                Present the requirements clearly and logically. Do not section by diagram type. Aim for maximum detail to enable accurate diagram generation.
                """, name, description);

            String detailedRequirements = chatModel.generate(prompt);
            logger.info("Generated detailed requirements string from LLM.");

            return detailedRequirements;

        } catch (Exception e) {
            logger.error("Error extracting detailed requirements: {}", e.getMessage(), e);
            return "";
        }
    }
} 