package com.marchina.controller;

import com.marchina.agent.MainAgent;
import com.marchina.agent.TTSAgent;
import com.marchina.model.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for handling agent-based processing requests.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    
    private final MainAgent mainAgent;
    private final TTSAgent ttsAgent;

    public AgentController(MainAgent mainAgent, TTSAgent ttsAgent) {
        this.mainAgent = mainAgent;
        this.ttsAgent = ttsAgent;
    }

    /**
     * Processes a request through the agent system.
     *
     * @param requestBody The request body containing the description
     * @return ResponseEntity containing the result or error
     */
    @PostMapping("/process")
    public ResponseEntity<AgentResponse> processRequest(@RequestBody Map<String, String> requestBody) {
        try {
            String description = requestBody.get("description");
            logger.info("Received agent processing request: {}", description);
            
            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new AgentResponse(false, "Description cannot be empty"));
            }
            
            // Process the request with the main agent
            AgentResponse result = mainAgent.processRequest(description);

            // Debugging code
            logger.info("Main agent response: {}", result);
            logger.info("Message to be sent to TTS: {}", result.getMessage());


            
            // Generate speech for the actual response message
            String speechData = ttsAgent.generateSpeech(result.getMessage());
            
            // Create a new response with the speech data
            AgentResponse responseWithSpeech = new AgentResponse(
                result.isSuccess(), 
                result.getMessage(),
                result.getResult(),
                speechData
            );
            
            return ResponseEntity.ok(responseWithSpeech);
        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new AgentResponse(false, "Error processing request: " + e.getMessage()));
        }
    }
}
