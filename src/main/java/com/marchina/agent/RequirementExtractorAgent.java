package com.marchina.agent;

import com.marchina.model.RequirementSet;
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

    public RequirementSet extractRequirements(String name, String description) {
        try {
            logger.info("Extracting requirements for project: {}", name);
            
            String prompt = String.format("""
                Given the following project details:
                Name: %s
                Description: %s
                
                Please analyze the project and provide requirements for the following diagram types:
                1. Entity Relationship Diagram (ERD)
                2. Flowchart
                3. Sequence Diagram
                4. Class Diagram
                
                Format your response in clear sections for each diagram type.
                """, name, description);

            String response = chatModel.generate(prompt);
            logger.info("Generated requirements from Azure OpenAI");
            
            RequirementSet requirements = new RequirementSet();
            requirements.setErdRequirements(extractSection(response, "Entity Relationship Diagram"));
            requirements.setFlowchartRequirements(extractSection(response, "Flowchart"));
            requirements.setSequenceDiagramRequirements(extractSection(response, "Sequence Diagram"));
            requirements.setClassDiagramRequirements(extractSection(response, "Class Diagram"));
            
            return requirements;
        } catch (Exception e) {
            logger.error("Error extracting requirements: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract requirements", e);
        }
    }

    private String extractSection(String content, String sectionName) {
        try {
            String[] sections = content.split("(?=" + sectionName + ")");
            for (String section : sections) {
                if (section.trim().startsWith(sectionName)) {
                    // Get the content until the next section or end of text
                    String[] parts = section.split("(?=\\d\\.)");
                    if (parts.length > 0) {
                        return parts[0].replace(sectionName, "").trim();
                    }
                }
            }
            return "";
        } catch (Exception e) {
            logger.error("Error extracting section {}: {}", sectionName, e.getMessage());
            return "";
        }
    }
} 