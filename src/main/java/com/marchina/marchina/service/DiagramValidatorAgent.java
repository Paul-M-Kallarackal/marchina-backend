package com.marchina.marchina.service;

import org.springframework.stereotype.Component;

@Component
public class DiagramValidatorAgent {
    public boolean validateMermaidCode(String mermaidCode) {
        // Basic validation rules
        if (mermaidCode == null || mermaidCode.trim().isEmpty()) {
            return false;
        }

        // Check for required Mermaid syntax
        if (!mermaidCode.contains("flowchart") && !mermaidCode.contains("erDiagram")) {
            return false;
        }

        // Check for proper node and connection syntax
        if (mermaidCode.contains("-->") || mermaidCode.contains("--|") || mermaidCode.contains("||--")) {
            return true;
        }

        // TODO: Add more sophisticated validation using Mermaid's parser
        // For now, we'll return true if basic checks pass
        return true;
    }
} 