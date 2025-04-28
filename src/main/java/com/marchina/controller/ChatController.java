package com.marchina.controller;

import com.marchina.agent.RequirementExtractorVoice;
import com.marchina.agent.RequirementExtractorVoice.ConversationState;
import com.marchina.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.marchina.config.JwtConfig.JwtService;

import java.util.Map;
import java.util.HashMap;


@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final RequirementExtractorVoice requirementExtractor;
    private final JwtService jwtService;  
    private final AgentController agentController;
    private final ProjectController projectController;


    public ChatController(
            RequirementExtractorVoice requirementExtractor,
            AgentController agentController,
            ProjectController projectController,
            JwtService jwtService) {
        this.requirementExtractor = requirementExtractor;
        this.agentController = agentController;
        this.projectController = projectController;
        this.jwtService = jwtService;

    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String userMessage = request.get("message");
            ChatResponse response = requirementExtractor.processMessage(userMessage, authHeader);

            // Get the state for this user
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtService.extractAllClaims(token);
            String userId = claims.get("userId").toString();
            ConversationState state = requirementExtractor.getUserState(userId);

            if (requirementExtractor.isRequirementsGathered(state)) {
                // Create project and generate diagrams
                Map<String, String> projectRequest = Map.of(
                    "name", requirementExtractor.getProjectName(state),
                    "description", requirementExtractor.getProjectDescription(state)
                );
                
                // Pass both the project request and auth header
                projectController.createProject(projectRequest, authHeader);
                agentController.processRequest(projectRequest);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in chat endpoint: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearConversation(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        Map<String, Object> claims = jwtService.extractAllClaims(token);
        String userId = claims.get("userId").toString();
        
        RequirementExtractorVoice.ConversationState state = requirementExtractor.getUserState(userId);
        if (state != null) {
            state.clearConversationHistory();
            logger.info("Cleared conversation history for user: {}", userId);
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Conversation history cleared successfully");
        return ResponseEntity.ok(response);
    }

}
