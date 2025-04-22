package com.marchina.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class DBConnectionController {
    private static final Logger logger = LoggerFactory.getLogger(DBConnectionController.class);
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DBConnectionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/connection")
    public ResponseEntity<String> testConnection() {
        try {
            String result = jdbcTemplate.queryForObject("SELECT version()", String.class);
            logger.info("Database connection successful. Version: {}", result);
            return ResponseEntity.ok("Database connection successful. Version: " + result);
        } catch (Exception e) {
            logger.error("Database connection failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Database connection failed: " + e.getMessage());
        }
    }

    @GetMapping("/tables")
    public ResponseEntity<List<String>> listTables() {
        try {
            String sql = """
                SELECT table_name 
                FROM information_schema.tables 
                WHERE table_schema = current_schema()
                AND table_type = 'BASE TABLE'
                """;
            
            List<String> tables = jdbcTemplate.queryForList(sql, String.class);
            logger.info("Found {} tables: {}", tables.size(), tables);
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            logger.error("Failed to list tables: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/schema")
    public ResponseEntity<List<Map<String, Object>>> getTableSchema() {
        try {
            String sql = """
                SELECT table_name, column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                ORDER BY table_name, ordinal_position
                """;
            
            List<Map<String, Object>> schema = jdbcTemplate.queryForList(sql);
            logger.info("Retrieved schema information for all tables");
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            logger.error("Failed to get schema information: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 