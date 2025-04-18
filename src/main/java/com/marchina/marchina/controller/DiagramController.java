package com.marchina.marchina.controller;


import com.marchina.marchina.model.DiagramRequest;
import com.marchina.marchina.model.DiagramResponse;
import com.marchina.marchina.service.DiagramManagerAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramManagerAgent diagramManager;

    @Autowired
    public DiagramController(DiagramManagerAgent diagramManager) {
        this.diagramManager = diagramManager;
    }

    @PostMapping("/flowchart")
    public ResponseEntity<DiagramResponse> generateFlowchart(@RequestBody DiagramRequest request) {
        String mermaidCode = diagramManager.generateDiagram(request.getPrompt(), DiagramManagerAgent.DiagramType.FLOWCHART);
        DiagramResponse response = new DiagramResponse(mermaidCode, "Flowchart generated successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/erd")
    public ResponseEntity<DiagramResponse> generateERD(@RequestBody DiagramRequest request) {
        String mermaidCode = diagramManager.generateDiagram(request.getPrompt(), DiagramManagerAgent.DiagramType.ERD);
        DiagramResponse response = new DiagramResponse(mermaidCode, "ERD generated successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<DiagramResponse> generateTest(@RequestBody DiagramRequest request) {
        String code  = "Testing CI/CD";
        DiagramResponse response = new DiagramResponse(code, "Test generated successfully");
        return ResponseEntity.ok(response);
    }
}