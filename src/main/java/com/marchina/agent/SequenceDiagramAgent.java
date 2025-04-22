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

@Component
public class SequenceDiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(SequenceDiagramAgent.class);
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

    public SequenceDiagramAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator, JdbcTemplate jdbcTemplate) {
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates and saves a sequence diagram for a project.
     */
    public void generateAndSaveSequenceDiagram(Long projectId, String requirements) {
        try {
            logger.info("Generating sequence diagram for project {}", projectId);
            
            AgentResponse response = generateSequenceDiagram(requirements);
            if (!response.isSuccess()) {
                throw new RuntimeException("Failed to generate sequence diagram: " + response.getMessage());
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
                "sequence",
                "Sequence Diagram",
                response.getMessage()
            );

            if (diagrams.isEmpty()) {
                throw new RuntimeException("Failed to save sequence diagram");
            }

            logger.info("Saved sequence diagram for project {}", projectId);
            
        } catch (Exception e) {
            logger.error("Error processing sequence diagram for project {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to process sequence diagram request", e);
        }
    }

    public AgentResponse generateSequenceDiagram(String description) {
        try {
            logger.info("Generating sequence diagram for description: {}", description);
            
            int retryCount = 0;
            String currentDescription = description;
            
            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate sequence diagram", retryCount + 1, MAX_RETRIES);
                
                String sequenceDiagramPrompt = String.format("""
                    Generate a Mermaid sequence diagram based on this description:
                    %s
                    
                    Follow these rules:
                    1. Use proper Mermaid sequence diagram syntax
                    2. Show all participants and their interactions
                    3. Include message types (sync/async)
                    4. Show activation/deactivation of participants
                    5. Add notes where necessary for clarity
                    6. Use proper time ordering
                    
                    Provide only the Mermaid code (in string not markdown), nothing else. Do not include quotes in the code.
                    """, currentDescription);
                
                String mermaidCode = chatModel.generate(sequenceDiagramPrompt);
                
                // Validate the generated sequence diagram
                String validationResult = diagramValidator.validateMermaidSyntax(mermaidCode);
                if (validationResult.contains("valid")) {
                    return new AgentResponse(true, mermaidCode, "Sequence diagram generated successfully");
                }
                
                // If validation fails, improve the description and retry
                String improvementPrompt = String.format("""
                    Improve this sequence diagram description based on validation feedback:
                    Original Description: %s
                    Generated Diagram: %s
                    Validation Feedback: %s
                    
                    Provide an improved version of the description.
                    """, description, mermaidCode, validationResult);
                
                currentDescription = chatModel.generate(improvementPrompt);
                retryCount++;
            }
            
            return new AgentResponse(false, 
                "Failed to generate valid sequence diagram after " + MAX_RETRIES + " attempts");
                
        } catch (Exception e) {
            logger.error("Error generating sequence diagram: {}", e.getMessage(), e);
            return new AgentResponse(false, "Error generating sequence diagram: " + e.getMessage());
        }
    }

    public String explainSequenceDiagram(String mermaidCode) {
        String prompt = String.format("""
            Explain the following Mermaid sequence diagram code in simple terms:
            %s
            
            Requirements:
            1. Explain the overall interaction flow
            2. Describe each participant's role
            3. Explain the sequence of messages
            4. Highlight important interactions
            5. Note any parallel or conditional flows
            
            Provide a clear and comprehensive explanation.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }
} 