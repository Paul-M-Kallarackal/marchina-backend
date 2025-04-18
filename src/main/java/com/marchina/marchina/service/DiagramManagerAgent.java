package com.marchina.marchina.service;


import org.springframework.stereotype.Component;

@Component
public class DiagramManagerAgent {
    private final FlowchartAgent flowchartAgent;
    private final ERDAgent erdAgent;
    private final DiagramValidatorAgent validatorAgent;

    public DiagramManagerAgent(FlowchartAgent flowchartAgent, 
                             ERDAgent erdAgent, 
                             DiagramValidatorAgent validatorAgent) {
        this.flowchartAgent = flowchartAgent;
        this.erdAgent = erdAgent;
        this.validatorAgent = validatorAgent;
    }

    public String generateDiagram(String prompt, DiagramType type) {
        DiagramAgent selectedAgent = switch (type) {
            case FLOWCHART -> flowchartAgent;
            case ERD -> erdAgent;
        };

        String mermaidCode = selectedAgent.generateDiagram(prompt);
        
        // Validate the generated code
        while (!validatorAgent.validateMermaidCode(mermaidCode)) {
            mermaidCode = selectedAgent.generateDiagram(prompt);
        }

        return mermaidCode;
    }

    public enum DiagramType {
        FLOWCHART,
        ERD
    }
} 