package com.marchina.controller;

import com.marchina.agent.MainAgent;
import com.marchina.model.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling agent-based processing requests.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    
    private final MainAgent mainAgent;

    public AgentController(MainAgent mainAgent) {
        this.mainAgent = mainAgent;
    }

    /**
     * Processes a request through the agent system.
     *
     * @param request The user's request
     * @return ResponseEntity containing the result or error
     */
    @PostMapping("/process")
    public ResponseEntity<AgentResponse> processRequest(@RequestBody String request) {
        try {
            logger.info("Received agent processing request: {}", request);
            AgentResponse result = mainAgent.processRequest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new AgentResponse(false, "Error processing request: " + e.getMessage()));
        }
    }

    @GetMapping("/test")
    public String test() {
        return "test";
    }

} 