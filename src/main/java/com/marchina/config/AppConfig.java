package com.marchina.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public Dotenv dotenv() {
        return Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
    }

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel(Dotenv dotenv) {
        logger.info("Initializing Azure OpenAI Chat Model");
        
        String apiKey = getEnvVariable(dotenv, "AZURE_OPENAI_API_KEY");
        String endpoint = getEnvVariable(dotenv, "AZURE_OPENAI_ENDPOINT");
        String deploymentId = getEnvVariable(dotenv, "AZURE_OPENAI_DEPLOYMENT_ID");
        String apiVersion = getEnvVariable(dotenv, "AZURE_OPENAI_API_VERSION");

        // Don't log the API key in production
        logger.info("Azure OpenAI Endpoint: {}", endpoint);
        logger.info("Azure OpenAI DeploymentId: {}", deploymentId);
        logger.info("Azure OpenAI API Version: {}", apiVersion);
        
        if (apiKey == null || endpoint == null || deploymentId == null || apiVersion == null) {
            String message = "Missing required Azure OpenAI configuration. Please check environment variables or .env file.";
            logger.error(message);
            throw new IllegalStateException(message);
        }
        
        try {
            return AzureOpenAiChatModel.builder()
                .apiKey(apiKey)
                .endpoint(endpoint)
                .deploymentName(deploymentId)
                .temperature(0.7)
                .build();
        } catch (Exception e) {
            String message = "Failed to initialize Azure OpenAI Chat Model: " + e.getMessage();
            logger.error(message, e);
            throw new IllegalStateException(message, e);
        }
    }
} 