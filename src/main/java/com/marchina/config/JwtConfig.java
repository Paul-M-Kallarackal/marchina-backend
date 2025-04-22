package com.marchina.config;

import com.marchina.model.User;
import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class JwtConfig {
    
    @Bean
    public JwtService jwtService(Dotenv dotenv) {
        return new JwtService(dotenv);
    }

    public static class JwtService {
        private final String SECRET_KEY;
        private final long EXPIRATION_TIME;

        public JwtService(Dotenv dotenv) {
            this.SECRET_KEY = dotenv.get("JWT_SECRET");
            this.EXPIRATION_TIME = Long.parseLong(dotenv.get("JWT_EXPIRATION"));
        }

        public String generateToken(User user) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole());
            claims.put("email", user.getEmail());
            claims.put("userId", user.getId());
            return createToken(claims, user.getId().toString());
        }

        private String createToken(Map<String, Object> claims, String subject) {
            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(subject)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                    .compact();
        }

        private Key getSigningKey() {
            byte[] keyBytes = SECRET_KEY.getBytes();
            return Keys.hmacShaKeyFor(keyBytes);
        }

        public String extractUserId(String token) {
            return extractClaim(token, Claims::getSubject);
        }

        public Long extractUserIdAsLong(String token) {
            String userId = extractUserId(token);
            return Long.parseLong(userId);
        }

        public String extractEmail(String token) {
            return extractClaim(token, claims -> claims.get("email", String.class));
        }

        public Date extractExpiration(String token) {
            return extractClaim(token, Claims::getExpiration);
        }

        public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
            final Claims claims = extractAllClaims(token);
            return claimsResolver.apply(claims);
        }

        public Claims extractAllClaims(String token) {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        }

        public Boolean isTokenExpired(String token) {
            return extractExpiration(token).before(new Date());
        }

        public Boolean validateToken(String token, User user) {
            final String userId = extractUserId(token);
            return (userId.equals(user.getId().toString()) && !isTokenExpired(token));
        }
    }
} 