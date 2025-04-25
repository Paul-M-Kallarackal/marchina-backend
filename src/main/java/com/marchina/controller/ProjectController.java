package com.marchina.controller;

import com.marchina.model.Project;
import com.marchina.model.RequirementSet;
import com.marchina.agent.MainAgent;
import com.marchina.agent.RequirementExtractorAgent;
import com.marchina.config.JwtConfig.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final MainAgent mainAgent;
    private final RequirementExtractorAgent requirementExtractorAgent;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProjectController(
            MainAgent mainAgent,
            RequirementExtractorAgent requirementExtractorAgent,
            JwtService jwtService,
            JdbcTemplate jdbcTemplate) {
        this.mainAgent = mainAgent;
        this.requirementExtractorAgent = requirementExtractorAgent;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
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

            // Extract requirements synchronously
            logger.info("Starting requirement extraction for project: {}", createdProject.getId());
            RequirementSet requirements = requirementExtractorAgent.extractRequirements(
                    createdProject.getName(),
                    createdProject.getDescription()
            );
            logger.info("Requirements: {}", requirements);
            // Generate diagrams synchronously
            processRequestsFromRequirements(createdProject.getId(), requirements);
            logger.info("Completed requirement extraction and diagram generation for project: {}", createdProject.getId());

            return ResponseEntity.ok(Map.of(
                "project", createdProject,
                "requirements", requirements
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

    public void processRequestsFromRequirements(Long projectId, RequirementSet requirements) {
        try {
            // Generate ERD
            if (requirements.getErdRequirements() != null && !requirements.getErdRequirements().isEmpty()) {
                mainAgent.processRequest(projectId, "ERD", requirements.getErdRequirements());
            }

            // Generate Flowchart
            if (requirements.getFlowchartRequirements() != null && !requirements.getFlowchartRequirements().isEmpty()) {
                mainAgent.processRequest(projectId, "Flowchart", requirements.getFlowchartRequirements());
            }

            // Generate Sequence Diagram
            if (requirements.getSequenceDiagramRequirements() != null && !requirements.getSequenceDiagramRequirements().isEmpty()) {
                mainAgent.processRequest(projectId, "Sequence Diagram", requirements.getSequenceDiagramRequirements());
            }

            // Generate Class Diagram
            if (requirements.getClassDiagramRequirements() != null && !requirements.getClassDiagramRequirements().isEmpty()) {
                mainAgent.processRequest(projectId, "Class Diagram", requirements.getClassDiagramRequirements());
            }
        } catch (Exception e) {
            logger.error("Error generating diagrams for project {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate diagrams", e);
        }
    }
} 