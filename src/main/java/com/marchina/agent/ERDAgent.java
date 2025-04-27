package com.marchina.agent;

// import com.marchina.model.AgentResponse;
import com.marchina.model.Diagram;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

// Added for JSON parsing
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map; // For parsing JSON
import java.util.Optional; // Import Optional

import com.marchina.model.Project;
import com.marchina.model.DiagramGenerationResult;

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
    private final ObjectMapper objectMapper; // For JSON parsing

    private final RowMapper<Diagram> diagramRowMapper = (rs, rowNum) -> {
        Diagram diagram = new Diagram();
        diagram.setId(rs.getLong("id"));
        diagram.setProjectId(rs.getLong("project_id"));
        diagram.setName(rs.getString("name"));
        diagram.setType(rs.getString("type"));
        diagram.setContent(rs.getString("content"));
        return diagram;
    };

    // Added objectMapper
    public ERDAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) { 
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper; // Inject ObjectMapper
    }

    /**
     * Generates and saves an ERD for a project.
     * @return The created Diagram object, or empty Optional if failed.
     */
    // Changed signature to return Optional<Diagram>
    public Optional<Diagram> generateAndSaveERD(Project project, String requirements) { 
        try {
            Long projectId = project.getId();
            logger.info("Attempting to generate ERD for project {} ('{}')", projectId, project.getName());

            DiagramGenerationResult result = generateERD(project, requirements);
            if (!result.success()) {
                logger.error("Failed to generate ERD content: {}", result.errorMessage());
                throw new RuntimeException("Failed to generate ERD content: " + result.errorMessage());
            }

            String diagramName = result.name();
            String mermaidCode = result.diagramCode();
            logger.info("Generated ERD content with name: '{}'", diagramName);

            String sql = """
                WITH inserted AS (
                    INSERT INTO "Diagrams" (project_id, name, type, content)
                    VALUES (?, ?, ?, ?)
                    RETURNING id, project_id, name, type, content
                )
                SELECT id, project_id, name, type, content
                FROM inserted
            """;

            Diagram createdDiagram = jdbcTemplate.queryForObject(
                sql,
                diagramRowMapper,
                projectId,
                diagramName, 
                "ERD", 
                mermaidCode
            );

            if (createdDiagram == null) {
                logger.error("Failed to save or retrieve ERD for project {}", projectId);
                throw new RuntimeException("Failed to save ERD");
            }

            logger.info("Saved ERD '{}' (ID: {}) for project {}", diagramName, createdDiagram.getId(), projectId);
            return Optional.of(createdDiagram); // Return the created diagram

        } catch (Exception e) {
            logger.error("Error processing ERD for project {}: {}", project.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process ERD request", e);
        }
    }

    /**
     * Generates an ERD based on the given description.
     *
     * @param project The project context
     * @param requirements The description of the ERD to generate
     * @return DiagramGenerationResult containing the generated ERD or error
     */
    // Updated to accept Project and return DiagramGenerationResult
    public DiagramGenerationResult generateERD(Project project, String requirements) {
        try {
            logger.info("Generating ERD for project: {}, requirements: {}", project.getId(), requirements);

            int retryCount = 0;
            String currentRequirements = requirements;
            String projectName = project.getName();
            String projectDescription = project.getDescription(); // Assuming Project has description

            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate ERD", retryCount + 1, MAX_RETRIES);

                // Update prompt to include project context and ask for JSON
                String erdPrompt = String.format("""
                    Project Context:
                    Name: %s
                    Description: %s

                    Requirements for ERD:
                    %s

                    Generate a Mermaid ERD (Entity Relationship Diagram) based on the project context and requirements.

                    Follow these rules for the ERD:
                    1. Use proper Mermaid ERD syntax.
                    2. Include all relevant entities with their attributes based on requirements.
                    3. Show relationships between entities clearly.
                    4. Use appropriate cardinality notation (e.g., ||, |o, }|, }o).
                    5. Include primary and foreign keys where applicable.
                    6. Add meaningful relationship descriptions.

                    Also, generate a concise and relevant name for this specific ERD based on the project and requirements.

                    Respond ONLY with a valid JSON object containing two keys: "name" (string) and "diagram" (string, the Mermaid code).
                    Example JSON response format:
                    {
                      "name": "E-commerce Database Schema ERD",
                      "diagram": "erDiagram\\nCUSTOMER ||--o{ ORDER : places\\n..."
                    }
                    Do not include any other text or markdown formatting outside the JSON object.
                    """, projectName, projectDescription, currentRequirements);

                String llmResponse = chatModel.generate(erdPrompt);

                try {
                    // Parse the JSON response
                    Map<String, String> parsedResponse = objectMapper.readValue(llmResponse, Map.class);
                    String diagramName = parsedResponse.get("name");
                    String mermaidCode = parsedResponse.get("diagram");

                    if (diagramName == null || diagramName.trim().isEmpty() || mermaidCode == null || mermaidCode.trim().isEmpty()) {
                         throw new JsonProcessingException("Missing 'name' or 'diagram' in LLM JSON response") {};
                    }

                    // Validate the generated ERD code (using specific ERD validator)
                    String validationResult = diagramValidator.validateERD(mermaidCode);
                    if (validationResult.contains("valid")) {
                        logger.info("Successfully generated and validated ERD. Name: '{}'", diagramName);
                        return DiagramGenerationResult.success(diagramName, mermaidCode);
                    } else {
                         logger.warn("Generated ERD failed validation (Attempt {}). Feedback: {}", retryCount + 1, validationResult);
                         currentRequirements = requirements; // Reset/refine requirements for retry
                         retryCount++;
                    }

                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse JSON response from LLM (Attempt {}): {}. Response: '{}'", retryCount + 1, e.getMessage(), llmResponse);
                    retryCount++; // Retry on parse failure
                }
            }

            logger.error("Failed to generate valid ERD after {} attempts for project {}", MAX_RETRIES, project.getId());
            return DiagramGenerationResult.failure("Failed to generate valid ERD after " + MAX_RETRIES + " attempts");

        } catch (Exception e) {
            logger.error("Error generating ERD for project {}: {}", project.getId(), e.getMessage(), e);
            return DiagramGenerationResult.failure("Error generating ERD: " + e.getMessage());
        }
    }

    // explainERD and generateSQL methods remain the same
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