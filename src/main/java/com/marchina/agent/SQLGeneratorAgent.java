package com.marchina.agent;

import com.marchina.model.AgentResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SQLGeneratorAgent {
    private static final Logger logger = LoggerFactory.getLogger(SQLGeneratorAgent.class);
    private final ChatLanguageModel chatModel;

    public SQLGeneratorAgent(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    public AgentResponse generateSQL(String description) {
        try {
            logger.info("Generating SQL for description: {}", description);
            
            String prompt = String.format("""
                Generate SQL CREATE TABLE statements based on this description:
                %s
                
                Follow these rules:
                1. Use proper SQL syntax
                2. Include appropriate data types
                3. Define primary and foreign keys
                4. Add necessary constraints
                5. Use clear and descriptive names
                
                Provide only the SQL statements, nothing else.
                """, description);
            
            String sql = chatModel.generate(prompt);
            return new AgentResponse(true, "SQL generated successfully", sql);
            
        } catch (Exception e) {
            logger.error("Error generating SQL: {}", e.getMessage(), e);
            return new AgentResponse(false, "Error generating SQL: " + e.getMessage());
        }
    }
} 