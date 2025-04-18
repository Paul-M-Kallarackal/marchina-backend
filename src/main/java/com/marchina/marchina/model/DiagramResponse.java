package com.marchina.marchina.model;

public class DiagramResponse {
    private String mermaidCode;
    private String message;

    public DiagramResponse() {
    }

    public DiagramResponse(String mermaidCode, String message) {
        this.mermaidCode = mermaidCode;
        this.message = message;
    }

    public String getMermaidCode() {
        return mermaidCode;
    }

    public void setMermaidCode(String mermaidCode) {
        this.mermaidCode = mermaidCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
} 