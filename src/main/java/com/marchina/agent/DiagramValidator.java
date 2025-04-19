package com.marchina.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DiagramValidator {
    private static final Logger logger = LoggerFactory.getLogger(DiagramValidator.class);
    private final ChatLanguageModel chatModel;

    public DiagramValidator(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    public String validateMermaidSyntax(String mermaidCode) {
        String prompt = String.format("""
            Validate this Mermaid diagram syntax:
            %s
            
            Check for:
            1. Proper syntax and structure
            2. Valid node and edge definitions
            3. Correct use of Mermaid keywords
            4. Complete and well-formed statements
            
            If valid, respond with 'valid'.
            If invalid, explain the specific issues found.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }

    public String validateFlowChart(String mermaidCode) {
        String prompt = String.format("""
            Validate this Mermaid flowchart:
            %s
            
            Check for:
            1. Proper flowchart syntax
            2. Clear start and end points
            3. Valid connections between nodes
            4. Proper decision points with all paths
            5. Logical flow and readability
            
            If valid, respond with 'valid'.
            If invalid, explain the specific issues found.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }

    public String validateERD(String mermaidCode) {
        String prompt = String.format("""
            Validate this Mermaid ERD:
            %s
            
            Check for:
            1. Proper ERD syntax
            2. Valid entity definitions
            3. Correct relationship notations
            4. Primary and foreign key definitions
            5. Complete and meaningful relationships
            
            If valid, respond with 'valid'.
            If invalid, explain the specific issues found.
            """, mermaidCode);
        
        return chatModel.generate(prompt);
    }

    public String suggestImprovements(String mermaidCode, String diagramType) {
        String prompt = String.format("""
            Analyze this %s diagram and suggest improvements:
            %s
            
            Consider:
            1. Clarity and readability
            2. Completeness of information
            3. Proper use of diagram conventions
            4. Logical organization
            5. Best practices for %s diagrams
            
            Provide specific, actionable suggestions for improvement.
            """, diagramType, mermaidCode, diagramType);
        
        return chatModel.generate(prompt);
    }
} 