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

@Component
public class ClassDiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ClassDiagramAgent.class);
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
    public ClassDiagramAgent(ChatLanguageModel chatModel, DiagramValidator diagramValidator, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) { 
        this.chatModel = chatModel;
        this.diagramValidator = diagramValidator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper; // Inject ObjectMapper
    }

    /**
     * Generates and saves a class diagram for a project.
     * @return The created Diagram object, or empty Optional if failed.
     */
    // Changed signature to return Optional<Diagram>
    public Optional<Diagram> generateAndSaveClassDiagram(Project project, String requirements) { 
        try {
            Long projectId = project.getId();
            logger.info("Attempting to generate class diagram for project {} ('{}')", projectId, project.getName());

            DiagramGenerationResult result = generateClassDiagram(project, requirements);
            if (!result.success()) {
                logger.error("Failed to generate class diagram content: {}", result.errorMessage());
                throw new RuntimeException("Failed to generate class diagram content: " + result.errorMessage());
            }

            String diagramName = result.name();
            String mermaidCode = result.diagramCode();
            logger.info("Generated class diagram content with name: '{}'", diagramName);

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
                "Class Diagram", 
                mermaidCode
            );

            if (createdDiagram == null) {
                logger.error("Failed to save or retrieve class diagram for project {}", projectId);
                throw new RuntimeException("Failed to save class diagram");
            }

            logger.info("Saved class diagram '{}' (ID: {}) for project {}", diagramName, createdDiagram.getId(), projectId);
            return Optional.of(createdDiagram); // Return the created diagram

        } catch (Exception e) {
            logger.error("Error processing class diagram for project {}: {}", project.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process class diagram request", e);
        }
    }

    // Updated to accept Project and return DiagramGenerationResult
    public DiagramGenerationResult generateClassDiagram(Project project, String requirements) {
        try {
            logger.info("Generating class diagram for project: {}, requirements: {}", project.getId(), requirements);

            int retryCount = 0;
            String currentRequirements = requirements;
            String projectName = project.getName();
            String projectDescription = project.getDescription(); // Assuming Project has description

            while (retryCount < MAX_RETRIES) {
                logger.info("Attempt {} of {} to generate class diagram", retryCount + 1, MAX_RETRIES);

                // Update prompt to include project context and ask for JSON
                String classDiagramPrompt = String.format("""
                    Project Context:
                    Name: %s
                    Description: %s

                    Requirements for Class Diagram:
                    %s

                    Generate a Mermaid class diagram based on the project context and requirements.

                    Follow these rules for the class diagram:
                    1. Use proper Mermaid class diagram syntax.
                    2. Include relevant classes with attributes and methods based on requirements.
                    3. Show relationships (inheritance, composition, aggregation, association) clearly.
                    4. Use correct notation for visibility (public +, private -, protected #).
                    5. Define data types for attributes and parameters where appropriate.

                    Also, generate a concise and relevant name for this specific class diagram based on the project and requirements.

                    Respond ONLY with a valid JSON object containing two keys: "name" (string) and "diagram" (string, the Mermaid code).
                    Example JSON response format:
                    {
                      "name": "Core Banking System Classes",
                      "diagram": "classDiagram\\nclass BankAccount{\\n+String accountNumber\\n...\\n}"
                    }
                    Do not include any other text or markdown formatting outside the JSON object.
                    """, projectName, projectDescription, currentRequirements);

                String llmResponse = chatModel.generate(classDiagramPrompt);

                try {
                    // Parse the JSON response
                    Map<String, String> parsedResponse = objectMapper.readValue(llmResponse, Map.class);
                    String diagramName = parsedResponse.get("name");
                    String mermaidCode = parsedResponse.get("diagram");

                    if (diagramName == null || diagramName.trim().isEmpty() || mermaidCode == null || mermaidCode.trim().isEmpty()) {
                         throw new JsonProcessingException("Missing 'name' or 'diagram' in LLM JSON response") {};
                    }

                    // Validate the generated class diagram code (using generic syntax validator)
                    String validationResult = diagramValidator.validateMermaidSyntax(mermaidCode);
                    if (validationResult.contains("valid")) {
                        logger.info("Successfully generated and validated class diagram. Name: '{}'", diagramName);
                        return DiagramGenerationResult.success(diagramName, mermaidCode);
                    } else {
                         logger.warn("Generated class diagram failed validation (Attempt {}). Feedback: {}", retryCount + 1, validationResult);
                         currentRequirements = requirements; // Reset/refine requirements for retry
                         retryCount++;
                    }

                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse JSON response from LLM (Attempt {}): {}. Response: '{}'", retryCount + 1, e.getMessage(), llmResponse);
                    retryCount++; // Retry on parse failure
                }
            }

            logger.error("Failed to generate valid class diagram after {} attempts for project {}", MAX_RETRIES, project.getId());
            return DiagramGenerationResult.failure("Failed to generate valid class diagram after " + MAX_RETRIES + " attempts");

        } catch (Exception e) {
            logger.error("Error generating class diagram for project {}: {}", project.getId(), e.getMessage(), e);
            return DiagramGenerationResult.failure("Error generating class diagram: " + e.getMessage());
        }
    }

    // explainClassDiagram method remains the same
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