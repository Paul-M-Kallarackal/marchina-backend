package com.marchina.marchina.service;

import org.springframework.stereotype.Component;

@Component
public class FlowchartAgent implements DiagramAgent {
    @Override
    public String generateDiagram(String prompt) {
        // TODO: Implement AI integration to generate flowchart
        // For now, return a sample flowchart
        return """
            flowchart TD
                A[Start] --> B{Is it working?}
                B -->|Yes| C[Great!]
                B -->|No| D[Fix it]
                D --> B
                C --> E[End]
            """;
    }
} 