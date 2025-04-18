package com.marchina.marchina.service;

import org.springframework.stereotype.Component;

@Component
public class ERDAgent implements DiagramAgent {
    @Override
    public String generateDiagram(String prompt) {
        // TODO: Implement AI integration to generate ERD
        // For now, return a sample ERD
        return """
            erDiagram
                CUSTOMER ||--o{ ORDER : places
                ORDER ||--|{ LINE-ITEM : contains
                CUSTOMER }|..|{ DELIVERY-ADDRESS : uses
            """;
    }
} 