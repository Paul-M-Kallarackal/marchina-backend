package com.marchina.controller;

import com.marchina.dto.AuthRequest;
import com.marchina.dto.AuthResponse;
import com.marchina.model.User;
import com.marchina.config.JwtConfig.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        return User.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .email(rs.getString("email"))
            .password(rs.getString("password"))
            .role(User.UserRole.valueOf(rs.getString("role")))
            .build();
    };

    @Autowired
    public UserController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        logger.info("UserController initialized");
        initializeGuestUser();
    }

    private void initializeGuestUser() {
        try {
            String checkSql = """
                            SELECT COUNT(*) FROM "Users" WHERE email = ?
                            """;
            int count = jdbcTemplate.queryForObject(checkSql, Integer.class, "guest@marchina.com");
            
            if (count == 0) {
                String sql = """
                        INSERT INTO "Users" (name, email, password, role)
                        VALUES (?, ?, ?, ?)
                        """;

                jdbcTemplate.update(sql,
                        "Guest User",
                        "guest@marchina.com",
                        passwordEncoder.encode("guestpass"),
                        User.UserRole.GUEST.name());
                
                logger.info("Guest user initialized successfully");
            }
        } catch (Exception e) {
            logger.error("Error initializing guest user: {}", e.getMessage(), e);
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signIn(@RequestBody AuthRequest request) {
        logger.info("Received sign in request for email: {}", request.getEmail());
        
        try {
            // Check if user exists
            String sql = "SELECT * FROM \"Users\" WHERE email = ?";
            List<User> users = jdbcTemplate.query(sql, userRowMapper, request.getEmail());
            
            if (users.isEmpty()) {
                // Create new user
                String createSql = """
                        INSERT INTO "Users" (name, email, password, role)
                        VALUES (?, ?, ?, ?)
                        RETURNING *
                        """;

                User newUser = jdbcTemplate.queryForObject(createSql, userRowMapper,
                        request.getEmail().split("@")[0], // Use email prefix as name
                        request.getEmail(),
                        passwordEncoder.encode(request.getPassword()),
                        User.UserRole.USER.name());

                assert newUser != null;
                String token = jwtService.generateToken(newUser);
                logger.info("Created new user with id: {}", newUser.getId());
                return ResponseEntity.ok(new AuthResponse(token, newUser));
            }
            
            // Existing user - verify password
            User user = users.get(0);
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(null, null));
            }
            
            String token = jwtService.generateToken(user);
            logger.info("Successfully logged in user with id: {}", user.getId());
            return ResponseEntity.ok(new AuthResponse(token, user));
        } catch (Exception e) {
            logger.error("Error during sign in: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new AuthResponse(null, null));
        }
    }

    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> guestSignIn() {
        logger.info("Received guest sign in request");
        
        try {
            String sql = "SELECT * FROM \"Users\" WHERE email = ?";
            List<User> users = jdbcTemplate.query(sql, userRowMapper, "guest@marchina.com");
            
            if (users.isEmpty()) {
                return ResponseEntity.badRequest().body(new AuthResponse(null, null));
            }
            
            User guestUser = users.get(0);
            String token = jwtService.generateToken(guestUser);
            
            logger.info("Successfully logged in guest user");
            return ResponseEntity.ok(new AuthResponse(token, guestUser));
        } catch (Exception e) {
            logger.error("Error during guest sign in: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new AuthResponse(null, null));
        }
    }

    @PostMapping("/signout")
    public ResponseEntity<Void> signOut() {
        return ResponseEntity.ok().build();
    }
}