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

            Map<String, Object> newProject = jdbcTemplate.queryForMap(
                createProjectSql,
                guestUser.get("id"),
                projectName,
                projectDescription
            );

            // Extract requirements synchronously
            logger.info("Starting requirement extraction for project: {}", newProject.get("id"));
            RequirementSet requirements = requirementExtractorAgent.extractRequirements(
                    projectName,
                    projectDescription
            );
            logger.info("Requirements: {}", requirements);

            // Generate diagrams synchronously using ProjectController's method
            projectController.processRequestsFromRequirements((Long) newProject.get("id"), requirements);
            logger.info("Completed requirement extraction and diagram generation for project: {}", newProject.get("id"));

            // Return user, project, and requirements info
            return ResponseEntity.ok(Map.of(
                "user", guestUser,
                "project", newProject,
                "requirements", requirements
            ));

        } catch (Exception e) {
            logger.error("Error creating guest project: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create guest project: " + e.getMessage()));
        }
    }
} 