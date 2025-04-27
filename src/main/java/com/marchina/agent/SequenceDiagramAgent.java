package com.marchina.agent;

// import com.marchina.model.AgentResponse; // Remove if unused
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

@Component
public class SequenceDiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(SequenceDiagramAgent.class);
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
    public SequenceDiagramAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper; // Inject ObjectMapper
    }

    /**
     * Generates and saves a sequence diagram for a project.
     * @return The created Diagram object, or empty Optional if failed.
     */
    // Changed signature to return Optional<Diagram>
    public Optional<Diagram> generateAndSaveSequenceDiagram(Project project, String requirements) {
        try {
            Long projectId = project.getId();
            logger.info("Attempting to generate sequence diagram for project {} ('{}')", projectId, project.getName());

            DiagramGenerationResult result = generateSequenceDiagram(project, requirements);
            if (!result.success()) {
                logger.error("Failed to generate sequence diagram content: {}", result.errorMessage());
                throw new RuntimeException("Failed to generate sequence diagram content: " + result.errorMessage());
            }

            String diagramName = result.name(); 
            String mermaidCode = result.diagramCode();
            logger.info("Generated sequence diagram content with name: '{}'", diagramName);

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
                "Sequence Diagram", 
                mermaidCode
            );

            if (createdDiagram == null) {
                logger.error("Failed to save or retrieve sequence diagram for project {}", projectId);
                throw new RuntimeException("Failed to save sequence diagram");
            }

            logger.info("Saved sequence diagram '{}' (ID: {}) for project {}", diagramName, createdDiagram.getId(), projectId);
            return Optional.of(createdDiagram); // Return the created diagram

        } catch (Exception e) {
            logger.error("Error processing sequence diagram for project {}: {}", project.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process sequence diagram request", e);
        }
    }

    // Updated to accept Project and return DiagramGenerationResult
    public DiagramGenerationResult generateSequenceDiagram(Project project, String requirements) {
        try {
            logger.info("Generating sequence diagram for project: {}, requirements: {}", project.getId(), requirements);

            int retryCount = 0;
            String currentRequirements = requirements;
            String projectName = project.getName();
            String projectDescription = project.getDescription(); // Assuming Project has description

            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate sequence diagram", retryCount + 1, MAX_RETRIES);

                // Update prompt to include project context and ask for JSON
                String sequenceDiagramPrompt = String.format("""
                    Project Context:
                    Name: %s
                    Description: %s

                    Requirements for Sequence Diagram:
                    %s

                    Generate a Mermaid sequence diagram based on the project context and requirements.

                    Follow these rules for the sequence diagram:
                    1. Use proper Mermaid sequence diagram syntax.
                    2. Show all relevant participants and their interactions based on requirements.
                    3. Include message types (sync/async) where appropriate.
                    4. Show activation/deactivation if needed for clarity.
                    5. Use proper time ordering.

                    Also, generate a concise and relevant name for this specific sequence diagram based on the project and requirements.

                    Respond ONLY with a valid JSON object containing two keys: "name" (string) and "diagram" (string, the Mermaid code).
                    Example JSON response format:
                    {
                      "name": "User Authentication Sequence",
                      "diagram": "sequenceDiagram\\nparticipant U as User\\nparticipant S as Server\\n..."
                    }
                    Do not include any other text or markdown formatting outside the JSON object.
                    """, projectName, projectDescription, currentRequirements);

                String llmResponse = chatModel.generate(sequenceDiagramPrompt);

                try {
                    // Parse the JSON response
                    Map<String, String> parsedResponse = objectMapper.readValue(llmResponse, Map.class);
                    String diagramName = parsedResponse.get("name");
                    String mermaidCode = parsedResponse.get("diagram");

                    if (diagramName == null || diagramName.trim().isEmpty() || mermaidCode == null || mermaidCode.trim().isEmpty()) {
                         throw new JsonProcessingException("Missing 'name' or 'diagram' in LLM JSON response") {};
                    }

                    // Validate the generated sequence diagram code (using generic syntax validator)
                    String validationResult = diagramValidator.validateMermaidSyntax(mermaidCode);
                    if (validationResult.contains("valid")) {
                        logger.info("Successfully generated and validated sequence diagram. Name: '{}'", diagramName);
                        return DiagramGenerationResult.success(diagramName, mermaidCode);
                    } else {
                         logger.warn("Generated sequence diagram failed validation (Attempt {}). Feedback: {}", retryCount + 1, validationResult);
                         currentRequirements = requirements; // Reset/refine requirements for retry
                         retryCount++;
                    }

                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse JSON response from LLM (Attempt {}): {}. Response: '{}'", retryCount + 1, e.getMessage(), llmResponse);
                    retryCount++; // Retry on parse failure
                }
            }

            logger.error("Failed to generate valid sequence diagram after {} attempts for project {}", MAX_RETRIES, project.getId());
            return DiagramGenerationResult.failure("Failed to generate valid sequence diagram after " + MAX_RETRIES + " attempts");

        } catch (Exception e) {
            logger.error("Error generating sequence diagram for project {}: {}", project.getId(), e.getMessage(), e);
            return DiagramGenerationResult.failure("Error generating sequence diagram: " + e.getMessage());
        }
    }

    // explainSequenceDiagram method remains the same
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