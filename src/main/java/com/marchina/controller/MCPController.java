package com.marchina.controller;

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

    @Autowired
    public MCPController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

            // Return both user and project info
            return ResponseEntity.ok(Map.of(
                "user", guestUser,
                "project", newProject
            ));

        } catch (Exception e) {
            logger.error("Error creating guest project: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create guest project: " + e.getMessage()));
        }
    }
} 