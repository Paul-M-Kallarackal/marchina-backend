package com.marchina.controller;

import com.marchina.agent.RequirementExtractorAgent;
import com.marchina.model.RequirementSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Need Project model for instantiation
import com.marchina.model.Project;

@RestController
@RequestMapping("/api/mcp")
public class MCPController {
    private static final Logger logger = LoggerFactory.getLogger(MCPController.class);
    private final JdbcTemplate jdbcTemplate;
    private final RequirementExtractorAgent requirementExtractorAgent;
    private final ProjectController projectController;

    @Autowired
    public MCPController(JdbcTemplate jdbcTemplate, 
                        RequirementExtractorAgent requirementExtractorAgent,
                        ProjectController projectController) {
        this.jdbcTemplate = jdbcTemplate;
        this.requirementExtractorAgent = requirementExtractorAgent;
        this.projectController = projectController;
    }

    @PostMapping("/guest/project")
    public ResponseEntity<?> createGuestProject(@RequestBody Map<String, String> payload) {
        try {
            // Get the existing guest user
            String getUserSql = """
                SELECT id, name, email, role 
                FROM "Users" 
                WHERE email = 'guest@marchina.com'
            """;

            Map<String, Object> guestUser = jdbcTemplate.queryForMap(getUserSql);

            // Create a project for the guest user
            if (!payload.containsKey("name") || !payload.containsKey("description")) {
                throw new IllegalArgumentException("Project name and description are required");
            }
            String projectName = payload.get("name");
            String projectDescription = payload.get("description");

            String createProjectSql = """
                WITH new_project AS (
                    INSERT INTO "Projects" (user_id, name, description)
                    VALUES (?, ?, ?)
                    RETURNING id, user_id, name, description
                )
                SELECT id, user_id, name, description FROM new_project
            """;

            Map<String, Object> newProjectMap = jdbcTemplate.queryForMap(
                createProjectSql,
                guestUser.get("id"),
                projectName,
                projectDescription
            );

            // Extract detailed requirements using the RENAMED method
            logger.info("Starting detailed requirement extraction for project: {}", newProjectMap.get("id"));
            // Use extractDetailedRequirements which returns String
            String detailedRequirements = requirementExtractorAgent.extractDetailedRequirements(
                    projectName,
                    projectDescription
            );
            logger.info("Detailed requirements extracted for guest project.");

            // Construct Project object from the map
            Project project = new Project();
            project.setId((Long) newProjectMap.get("id"));
            project.setUserId((Long) newProjectMap.get("user_id"));
            project.setName((String) newProjectMap.get("name"));
            project.setDescription((String) newProjectMap.get("description"));
            
            // Call the appropriate method in ProjectController (which handles diagram generation)
            // We pass the project object and the extracted detailed requirements string
            projectController.generateOptimalDiagram(project, detailedRequirements); 
            logger.info("Completed diagram generation request for guest project: {}", project.getId());

            // Return user, project (as map), and the detailed requirements string
            return ResponseEntity.ok(Map.of(
                "user", guestUser,
                "project", newProjectMap, 
                "detailedRequirements", detailedRequirements 
            ));

        } catch (Exception e) {
            logger.error("Error creating guest project: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create guest project: " + e.getMessage()));
        }
    }
} 