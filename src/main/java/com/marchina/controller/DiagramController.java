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
                return ResponseEntity.notFound().build();
            }

            String generalType = payload.get("generalType");
            String requirement = payload.get("requirement");

            if (generalType == null || requirement == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Both generalType and requirement are required"));
            }

            // Map general type to specific diagram type
            String diagramType;
            switch (generalType) {
                case "System Architecture" -> diagramType = "Class Diagram";
                case "Workflow" -> diagramType = "Flowchart";
                case "Database Schema" -> diagramType = "ERD";
                default -> {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid generalType. Must be one of: System Architecture, Workflow, or Database Schema"));
                }
            }

            logger.info("Creating {} diagram for project {}", diagramType, projectId);
            
            // Process the request using MainAgent
            mainAgent.processRequest(projectId, diagramType, requirement);

            // Fetch the newly created diagram
            String selectSql = """
                SELECT id, project_id, name, type, content
                FROM "Diagrams"
                WHERE project_id = ?
                ORDER BY id DESC
                LIMIT 1
            """;

            List<Diagram> diagrams = jdbcTemplate.query(selectSql, diagramRowMapper, projectId);
            
            if (diagrams.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Failed to create diagram"));
            }

            return ResponseEntity.ok(diagrams.get(0));
        } catch (Exception e) {
            logger.error("Error creating diagram for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create diagram: " + e.getMessage()));
        }
    }
} 