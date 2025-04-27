package com.marchina.agent;

// Remove AgentResponse import if no longer needed directly
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

import com.marchina.model.Project; // Import Project model
import com.marchina.model.DiagramGenerationResult; // Import DiagramGenerationResult

@Component
public class FlowchartAgent {
    private static final Logger logger = LoggerFactory.getLogger(FlowchartAgent.class);
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

    // Add ObjectMapper to constructor
    public FlowchartAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper; // Inject ObjectMapper
    }

    /**
     * Generates and saves a flowchart for a project.
     * @return The created Diagram object, or empty Optional if failed.
     */
    // Changed return type to Optional<Diagram>
    public Optional<Diagram> generateAndSaveFlowchart(Project project, String requirements) {
        try {
            Long projectId = project.getId();
            logger.info("Attempting to generate flowchart for project {} ('{}')", projectId, project.getName());

            DiagramGenerationResult result = generateFlowchart(project, requirements);
            if (!result.success()) {
                 logger.error("Failed to generate flowchart content: {}", result.errorMessage());
                // Throw exception or return empty to indicate failure
                 throw new RuntimeException("Failed to generate flowchart content: " + result.errorMessage());
                 // return Optional.empty(); 
            }

            String diagramName = result.name();
            String mermaidCode = result.diagramCode();
            logger.info("Generated flowchart content with name: '{}'", diagramName);

            String sql = """
                WITH inserted AS (
                    INSERT INTO "Diagrams" (project_id, name, type, content)
                    VALUES (?, ?, ?, ?)
                    RETURNING id, project_id, name, type, content
                )
                SELECT id, project_id, name, type, content
                FROM inserted
            """;

            // Use queryForObject if expecting exactly one row
            Diagram createdDiagram = jdbcTemplate.queryForObject(
                sql,
                diagramRowMapper,
                projectId,
                diagramName, 
                "Flowchart",
                mermaidCode
            );

            if (createdDiagram == null) {
                 logger.error("Failed to save or retrieve flowchart diagram for project {}", projectId);
                 throw new RuntimeException("Failed to save flowchart diagram");
                 // return Optional.empty(); 
            }

            logger.info("Saved flowchart '{}' (ID: {}) for project {}", diagramName, createdDiagram.getId(), projectId);
            return Optional.of(createdDiagram); // Return the created diagram

        } catch (Exception e) {
             // Log specific jdbcTemplate errors if possible (e.g., DataAccessException)
            logger.error("Error processing flowchart for project {}: {}", project.getId(), e.getMessage(), e);
             // Re-throw or return empty based on desired error handling for the caller
            throw new RuntimeException("Failed to process flowchart request", e);
             // return Optional.empty(); 
        }
    }

    // Updated to accept Project and return DiagramGenerationResult
    public DiagramGenerationResult generateFlowchart(Project project, String requirements) {
        try {
            logger.info("Generating flowchart for project: {}, requirements: {}", project.getId(), requirements);

            int retryCount = 0;
            String currentRequirements = requirements;
            String projectName = project.getName();
            String projectDescription = project.getDescription(); // Assuming Project has description

            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate flowchart", retryCount + 1, MAX_RETRIES);

                // Update prompt to include project context and ask for JSON
                String flowchartPrompt = String.format("""
                    Project Context:
                    Name: %s
                    Description: %s

                    Requirements for Flowchart:
                    %s

                    Generate a Mermaid flowchart based on the project context and requirements.

                    Follow these rules for the flowchart:
                    1. Use proper Mermaid flowchart syntax.
                    2. Include necessary steps and decision points based on requirements.
                    3. Use clear directional flow.
                    4. Add appropriate labels.
                    5. Keep it clear and readable.

                    Also, generate a concise and relevant name for this specific flowchart based on the project and requirements.

                    Respond ONLY with a valid JSON object containing two keys: "name" (string) and "diagram" (string, the Mermaid code).
                    Example JSON response format:
                    {
                      "name": "User Login Process Flowchart",
                      "diagram": "graph TD\\nA[Start] --> B{User Logs In?};\\n..."
                    }
                    Do not include any other text or markdown formatting outside the JSON object.
                    """, projectName, projectDescription, currentRequirements);

                String llmResponse = chatModel.generate(flowchartPrompt);

                try {
                    // Parse the JSON response
                    Map<String, String> parsedResponse = objectMapper.readValue(llmResponse, Map.class);
                    String diagramName = parsedResponse.get("name");
                    String mermaidCode = parsedResponse.get("diagram");

                    if (diagramName == null || diagramName.trim().isEmpty() || mermaidCode == null || mermaidCode.trim().isEmpty()) {
                         throw new JsonProcessingException("Missing 'name' or 'diagram' in LLM JSON response") {};
                    }

                    // Validate the generated flowchart code
                    String validationResult = diagramValidator.validateFlowChart(mermaidCode);
                    if (validationResult.contains("valid")) {
                        logger.info("Successfully generated and validated flowchart. Name: '{}'", diagramName);
                        return DiagramGenerationResult.success(diagramName, mermaidCode);
                    } else {
                         // If validation fails, log it and prepare for retry (or fail after max retries)
                         logger.warn("Generated flowchart failed validation (Attempt {}). Feedback: {}", retryCount + 1, validationResult);
                         // Potentially use validationResult to improve requirements for retry, omitted for brevity
                         currentRequirements = requirements; // Reset/refine requirements for retry
                         retryCount++;
                    }

                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse JSON response from LLM (Attempt {}): {}. Response: '{}'", retryCount + 1, e.getMessage(), llmResponse);
                    retryCount++; // Retry even on parse failure, maybe LLM corrects itself
                }
            }

            logger.error("Failed to generate valid flowchart after {} attempts for project {}", MAX_RETRIES, project.getId());
            return DiagramGenerationResult.failure("Failed to generate valid flowchart after " + MAX_RETRIES + " attempts");

        } catch (Exception e) {
            logger.error("Error generating flowchart for project {}: {}", project.getId(), e.getMessage(), e);
            return DiagramGenerationResult.failure("Error generating flowchart: " + e.getMessage());
        }
    }


    // explainFlowChart method remains the same
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