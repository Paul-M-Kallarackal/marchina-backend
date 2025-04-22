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
public class ClassDiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ClassDiagramAgent.class);
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

    public ClassDiagramAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator, JdbcTemplate jdbcTemplate) {
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates and saves a class diagram for a project.
     */
    public void generateAndSaveClassDiagram(Long projectId, String requirements) {
        try {
            logger.info("Generating class diagram for project {}", projectId);
            
            AgentResponse response = generateClassDiagram(requirements);
            if (!response.isSuccess()) {
                throw new RuntimeException("Failed to generate class diagram: " + response.getMessage());
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
                "class",
                "Class Diagram",
                response.getMessage()
            );

            if (diagrams.isEmpty()) {
                throw new RuntimeException("Failed to save class diagram");
            }

            logger.info("Saved class diagram for project {}", projectId);
            
        } catch (Exception e) {
            logger.error("Error processing class diagram for project {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to process class diagram request", e);
        }
    }

    public AgentResponse generateClassDiagram(String description) {
        try {
            logger.info("Generating class diagram for description: {}", description);
            
            int retryCount = 0;
            String currentDescription = description;
            
            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate class diagram", retryCount + 1, MAX_RETRIES);
                
                String classDiagramPrompt = String.format("""
                    Generate a Mermaid class diagram based on this description:
                    %s
                    
                    Follow these rules:
                    1. Use proper Mermaid class diagram syntax
                    2. Include all necessary classes and interfaces
                    3. Show inheritance, composition, and aggregation relationships
                    4. Include important methods and attributes
                    5. Use appropriate access modifiers (public, private, protected)
                    6. Add meaningful relationship descriptions
                    
                    Provide only the Mermaid code (in string not markdown), nothing else. Do not include quotes in the code.
                    """, currentDescription);
                
                String mermaidCode = chatModel.generate(classDiagramPrompt);
                
                // Validate the generated class diagram
                String validationResult = diagramValidator.validateMermaidSyntax(mermaidCode);
                if (validationResult.contains("valid")) {
                    return new AgentResponse(true, mermaidCode, "Class diagram generated successfully");
                }
                
                // If validation fails, improve the description and retry
                String improvementPrompt = String.format("""
                    Improve this class diagram description based on validation feedback:
                    Original Description: %s
                    Generated Diagram: %s
                    Validation Feedback: %s
                    
                    Provide an improved version of the description.
                    """, description, mermaidCode, validationResult);
                
                currentDescription = chatModel.generate(improvementPrompt);
                retryCount++;
            }
            
            return new AgentResponse(false, 
                "Failed to generate valid class diagram after " + MAX_RETRIES + " attempts");
                
        } catch (Exception e) {
            logger.error("Error generating class diagram: {}", e.getMessage(), e);
            return new AgentResponse(false, "Error generating class diagram: " + e.getMessage());
        }
    }

    public String explainClassDiagram(String mermaidCode) {
        String prompt = String.format("""
            Explain the following Mermaid class diagram code in simple terms:
            %s
            
            Requirements:
            1. Explain the overall structure and relationships
            2. Describe each class's purpose and responsibilities
            3. Highlight important methods and attributes
            4. Explain inheritance hierarchies and relationships
            5. Note any design patterns or architectural decisions
            
            Provide a clear and comprehensive explanation.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }
} 