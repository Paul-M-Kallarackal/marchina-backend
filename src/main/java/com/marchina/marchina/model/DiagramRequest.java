package com.marchina.marchina.model;

public class DiagramRequest {
    private String prompt;

    public DiagramRequest() {
    }

    public DiagramRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
} 