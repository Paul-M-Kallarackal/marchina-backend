package com.marchina.agent;

import com.marchina.model.AgentResponse;
import com.marchina.model.Diagram;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent responsible for generating Entity Relationship Diagrams (ERDs).
 */
@Component
public class ERDAgent {
    private static final Logger logger = LoggerFactory.getLogger(ERDAgent.class);
    private static final int MAX_RETRIES = 3;

    private final ChatLanguageModel chatModel;
    private final DiagramValidator diagramValidator;
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Diagram> diagramRowMapper = (rs, rowNum) -> {
        Diagram diagram = new Diagram();
        diagram.setId(rs.getLong("id"));
        diagram.setProjectId(rs.getLong("project_id"));
        diagram.setName(rs.getString("name"));
        diagram.setType(rs.getString("type"));
        diagram.setContent(rs.getString("content"));
        return diagram;
    };

    public ERDAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator, JdbcTemplate jdbcTemplate) {
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates and saves an ERD for a project.
     */
    public void generateAndSaveERD(Long projectId, String requirements) {
        try {
            logger.info("Generating ERD for project {}", projectId);
            
            AgentResponse response = generateERD(requirements);
            if (!response.isSuccess()) {
                throw new RuntimeException("Failed to generate ERD: " + response.getMessage());
            }

            String sql = """
                WITH inserted AS (
                    INSERT INTO "Diagrams" (project_id, name, type, content)
                    VALUES (?, ?, ?, ?)
                    RETURNING id, project_id, name, type, content
                )
                SELECT id, project_id, name, type, content
                FROM inserted
            """;

            List<Diagram> diagrams = jdbcTemplate.query(
                sql,
                diagramRowMapper,
                projectId,
                "erd",
                "Entity Relationship Diagram",
                response.getMessage()
            );

            if (diagrams.isEmpty()) {
                throw new RuntimeException("Failed to save ERD");
            }

            logger.info("Saved ERD for project {}", projectId);
            
        } catch (Exception e) {
            logger.error("Error processing ERD for project {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to process ERD request", e);
        }
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
                
                String erdPrompt = String.format("""
                    Generate a Mermaid ERD based on this description:
                    %s
                    
                    Follow these rules:
                    1. Use proper Mermaid ERD syntax
                    2. Include all entities with their attributes
                    3. Show relationships between entities
                    4. Use proper cardinality notation
                    5. Include primary and foreign keys
                    6. Add meaningful relationship descriptions
                    
                    Provide only the Mermaid code (in string not markdown), nothing else. Do not include quotes in the code.
                    """, currentDescription);
                
                String mermaidCode = chatModel.generate(erdPrompt);
                
                // Validate the generated ERD
                String validationResult = diagramValidator.validateMermaidSyntax(mermaidCode);
                if (validationResult.contains("valid")) {
                    return new AgentResponse(true, mermaidCode, "ERD generated successfully");
                }
                
                // If validation fails, improve the description and retry
                String improvementPrompt = String.format("""
                    Improve this ERD description based on validation feedback:
                    Original Description: %s
                    Generated Diagram: %s
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
            
            Requirements:
            1. Explain the overall data structure
            2. Describe each entity and its attributes
            3. Explain relationships between entities
            4. Highlight key constraints and cardinalities
            5. Note any important design decisions
            
            Provide a clear and comprehensive explanation.
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