package com.marchina.controller;

import com.marchina.agent.RequirementExtractorVoice;
import com.marchina.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = {"http://localhost:3000", "https://marchina.calmmoss-a81a16c4.eastus.azurecontainerapps.io"})
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final RequirementExtractorVoice requirementExtractor;
    private final AgentController agentController;
    private final ProjectController projectController;

    public ChatController(
            RequirementExtractorVoice requirementExtractor,
            AgentController agentController,
            ProjectController projectController) {
        this.requirementExtractor = requirementExtractor;
        this.agentController = agentController;
        this.projectController = projectController;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String userMessage = request.get("message");
            ChatResponse response = requirementExtractor.processMessage(userMessage, authHeader);

            if (requirementExtractor.isRequirementsGathered()) {
                // Create project and generate diagrams
                Map<String, String> projectRequest = Map.of(
                    "name", requirementExtractor.getProjectName(),
                    "description", requirementExtractor.getProjectDescription()
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

}
