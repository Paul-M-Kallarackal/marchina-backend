package com.marchina.controller;

import com.marchina.model.Diagram;
import com.marchina.config.JwtConfig.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Map;
import com.marchina.agent.MainAgent;
import com.marchina.model.Project;
import java.util.Optional;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/projects/{projectId}/diagrams")
public class DiagramController {
    private static final Logger logger = LoggerFactory.getLogger(DiagramController.class);

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final MainAgent mainAgent;

    @Autowired
    public DiagramController(JwtService jwtService, JdbcTemplate jdbcTemplate, MainAgent mainAgent) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.mainAgent = mainAgent;
        logger.info("DiagramController initialized");
    }

    private final RowMapper<Diagram> diagramRowMapper = (rs, rowNum) -> {
        Diagram diagram = new Diagram();
        diagram.setId(rs.getLong("id"));
        diagram.setProjectId(rs.getLong("project_id"));
        diagram.setName(rs.getString("name"));
        diagram.setType(rs.getString("type"));
        diagram.setContent(rs.getString("content"));
        return diagram;
    };

    private final RowMapper<Project> projectRowMapper = (rs, rowNum) -> {
        Project project = new Project();
        project.setId(rs.getLong("id"));
        project.setUserId(rs.getLong("user_id"));
        project.setName(rs.getString("name"));
        project.setDescription(rs.getString("description"));
        return project;
    };

    private boolean validateProjectAccess(Long projectId, Long userId) {
        String sql = "SELECT COUNT(*) FROM \"Projects\" WHERE id = ? AND user_id = ?";
        int count = jdbcTemplate.queryForObject(sql, Integer.class, projectId, userId);
        return count > 0;
    }

    @GetMapping
    public ResponseEntity<?> getDiagrams(
            @PathVariable Long projectId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());

            if (!validateProjectAccess(projectId, userId)) {
                return ResponseEntity.notFound().build();
            }

            String sql = """
                SELECT id, project_id, name, type, content
                FROM "Diagrams"
                WHERE project_id = ?
            """;

            List<Diagram> diagrams = jdbcTemplate.query(sql, diagramRowMapper, projectId);
            return ResponseEntity.ok(diagrams);
        } catch (Exception e) {
            logger.error("Error getting diagrams for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{diagramId}")
    public ResponseEntity<?> getDiagram(
            @PathVariable Long projectId,
            @PathVariable Long diagramId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());

            if (!validateProjectAccess(projectId, userId)) {
                return ResponseEntity.notFound().build();
            }

            String sql = """
                SELECT id, project_id, name, type, content
                FROM "Diagrams"
                WHERE id = ? AND project_id = ?
            """;

            List<Diagram> diagrams = jdbcTemplate.query(sql, diagramRowMapper, diagramId, projectId);
            
            if (diagrams.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(diagrams.get(0));
        } catch (Exception e) {
            logger.error("Error getting diagram {}: {}", diagramId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{diagramId}")
    public ResponseEntity<?> updateDiagram(
            @PathVariable Long projectId,
            @PathVariable Long diagramId,
            @RequestBody Diagram updatedDiagram,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());

            if (!validateProjectAccess(projectId, userId)) {
                return ResponseEntity.notFound().build();
            }

            String sql = """
                UPDATE "Diagrams"
                SET name = ?,
                    content = ?,
                    type = ?
                WHERE id = ?
                AND project_id = ?
                RETURNING id, project_id, name, type, content
            """;

            List<Diagram> updatedDiagrams = jdbcTemplate.query(
                sql,
                diagramRowMapper,
                updatedDiagram.getName(),
                updatedDiagram.getContent(),
                updatedDiagram.getType(),
                diagramId,
                projectId
            );

            if (updatedDiagrams.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(updatedDiagrams.get(0));
        } catch (Exception e) {
            logger.error("Error updating diagram {}: {}", diagramId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to update diagram: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{diagramId}")
    public ResponseEntity<?> deleteDiagram(
            @PathVariable Long projectId,
            @PathVariable Long diagramId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());

            if (!validateProjectAccess(projectId, userId)) {
                return ResponseEntity.notFound().build();
            }

            String sql = "DELETE FROM \"Diagrams\" WHERE id = ? AND project_id = ?";
            int rowsAffected = jdbcTemplate.update(sql, diagramId, projectId);

            if (rowsAffected == 0) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error deleting diagram {}: {}", diagramId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to delete diagram: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createDiagram(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            Long userId = Long.parseLong(claims.get("userId").toString());

            if (!validateProjectAccess(projectId, userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Project not found or access denied"));
            }

            String projectSql = "SELECT id, user_id, name, description FROM \"Projects\" WHERE id = ? AND user_id = ?";
            List<Project> projects = jdbcTemplate.query(projectSql, projectRowMapper, projectId, userId);

            if (projects.isEmpty()) {
                logger.warn("Project with ID {} not found or user {} does not have access.", projectId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Project not found or access denied"));
            }
            Project project = projects.get(0);

            String generalType = payload.get("generalType");
            String requirement = payload.get("requirement");

            if (generalType == null || requirement == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Both generalType and requirement are required"));
            }

            String diagramType;
            switch (generalType) {
                case "System Architecture" -> diagramType = "Class Diagram";
                case "Workflow" -> diagramType = "Flowchart";
                case "Database Schema" -> diagramType = "ERD";
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid generalType specified"));
                }
            }

            logger.info("Requesting creation of {} diagram for project {}", diagramType, projectId);

            Optional<Diagram> createdDiagramOpt = mainAgent.processRequest(project, diagramType, requirement);

            if (createdDiagramOpt.isPresent()) {
                logger.info("Successfully created diagram ID {} for project {}", createdDiagramOpt.get().getId(), projectId);
                return ResponseEntity.status(HttpStatus.CREATED).body(createdDiagramOpt.get());
            } else {
                logger.error("Diagram creation process completed for project {}, but no diagram object was returned.", projectId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(Map.of("error", "Failed to create diagram: process completed without result"));
            }

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request during diagram creation for project {}: {}", projectId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("Error creating diagram for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create diagram: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error creating diagram for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred during diagram creation"));
        }
    }
} 