package com.marchina.controller;

import com.marchina.model.Project;
import com.marchina.agent.MainAgent;
import com.marchina.agent.RequirementExtractorAgent;
import com.marchina.config.JwtConfig.JwtService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final MainAgent mainAgent;
    private final RequirementExtractorAgent requirementExtractorAgent;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final ChatLanguageModel chatModel;

    @Autowired
    public ProjectController(
            MainAgent mainAgent,
            RequirementExtractorAgent requirementExtractorAgent,
            JwtService jwtService,
            JdbcTemplate jdbcTemplate,
            ChatLanguageModel chatModel) {
        this.mainAgent = mainAgent;
        this.requirementExtractorAgent = requirementExtractorAgent;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.chatModel = chatModel;
        logger.info("ProjectController initialized");
    }

    private final RowMapper<Project> projectRowMapper = (rs, rowNum) -> {
        Project project = new Project();
        project.setId(rs.getLong("id"));
        project.setUserId(rs.getLong("user_id"));
        project.setName(rs.getString("name"));
        project.setDescription(rs.getString("description"));
        return project;
    };

    @GetMapping
    public ResponseEntity<List<Project>> getUserProjects(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());
            logger.info("Getting projects for user ID: {}", userId);

            String sql = """
                SELECT id, user_id, name, description
                FROM "Projects" WHERE user_id = ?
                ORDER BY id DESC
            """;
            
            List<Project> projects = jdbcTemplate.query(sql, projectRowMapper, userId);
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            logger.error("Error getting user projects: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Map<String, String> payload,
                                         @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());
            
            String name = payload.get("name");
            String description = payload.get("description");

            String sql = """
                INSERT INTO "Projects" (user_id, name, description)
                VALUES (?, ?, ?)
                RETURNING id, user_id, name, description
            """;

            List<Project> projects = jdbcTemplate.query(sql, projectRowMapper, userId, name, description);
            
            if (projects.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Failed to create project"));
            }

            Project createdProject = projects.get(0);

            // Extract detailed requirements as a single string
            logger.info("Starting detailed requirement extraction for project: {}", createdProject.getId());
            String detailedRequirements = requirementExtractorAgent.extractDetailedRequirements(
                    createdProject.getName(),
                    createdProject.getDescription()
            );
            logger.info("Detailed requirements extracted.");

            // Determine and generate the single most optimal diagram
            generateOptimalDiagram(createdProject, detailedRequirements);
            logger.info("Completed diagram generation for project: {}", createdProject.getId());

            return ResponseEntity.ok(Map.of(
                "project", createdProject,
                "detailedRequirements", detailedRequirements
            ));
        } catch (Exception e) {
            logger.error("Error creating project: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create project: " + e.getMessage()));
        }
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());
            logger.info("Getting project with ID: {} for user: {}", id, userId);
            
            // Get project details
            String projectSql = """
                SELECT id, user_id, name, description
                FROM "Projects"
                WHERE id = ? AND user_id = ?
            """;
            
            List<Project> projects = jdbcTemplate.query(projectSql, projectRowMapper, id, userId);
            
            if (projects.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(projects.get(0));
        } catch (Exception e) {
            logger.error("Error getting project: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    public void generateOptimalDiagram(Project project, String detailedRequirements) {
        try {
            if (detailedRequirements == null || detailedRequirements.trim().isEmpty()) {
                 logger.warn("Skipping diagram generation for project {} due to empty detailed requirements.", project.getId());
                return;
            }

            // Determine the optimal diagram type using LLM
            String analysisPrompt = String.format("""
                Analyze these detailed project requirements and determine the single most appropriate diagram type to visualize them.
                Available types: ERD, Flowchart, Sequence Diagram, Class Diagram.
                Consider the focus of the requirements (data structure, process flow, interactions, object structure).
                
                Requirements:
                %s
                
                Respond ONLY with the name of the single most appropriate diagram type (e.g., Flowchart, ERD, Sequence Diagram, Class Diagram).
                """, detailedRequirements);

            String optimalDiagramType = chatModel.generate(analysisPrompt).trim();
            // Basic validation/fallback - might need more robust handling
            List<String> validTypes = List.of("ERD", "Flowchart", "Sequence Diagram", "Class Diagram");
            if (!validTypes.contains(optimalDiagramType)) {
                 logger.warn("LLM returned invalid diagram type '{}'. Defaulting to Flowchart.", optimalDiagramType);
                 optimalDiagramType = "Flowchart"; // Fallback to Flowchart
            }

            logger.info("Determined optimal diagram type for project {}: {}", project.getId(), optimalDiagramType);
            
            // Call MainAgent to generate the determined optimal diagram type
            mainAgent.processRequest(project, optimalDiagramType, detailedRequirements); 

        } catch (Exception e) {
            logger.error("Error determining or generating optimal diagram for project {}: {}", project.getId(), e.getMessage(), e);
            // Consider re-throwing depending on desired behavior
        }
    }
} 