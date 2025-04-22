package com.marchina.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {


    @Bean
    public DataSource dataSource(Dotenv dotenv) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");
        
        config.setJdbcUrl(dotenv.get("DB_URL"));
        config.setUsername(dotenv.get("DB_USERNAME")); 
        config.setPassword(dotenv.get("DB_PASSWORD"));
        
        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        config.setMaxLifetime(1200000);
        config.setAutoCommit(true);
        config.setPoolName("MarchinaHikariPool");
        
        // Connection test settings
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
} 