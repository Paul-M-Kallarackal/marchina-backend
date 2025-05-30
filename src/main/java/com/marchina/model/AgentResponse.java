package com.marchina.model;

public class AgentResponse {
    private boolean success;
    private String message;
    private String result;
    private String speechData;

    public AgentResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.result = null;
        this.speechData = null;
    }

    public AgentResponse(boolean success, String message, String result) {
        this.success = success;
        this.message = message;
        this.result = result;
        this.speechData = null;
    }

    public AgentResponse(boolean success, String message, String result, String speechData) {
        this.success = success;
        this.message = message;
        this.result = result;
        this.speechData = speechData;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
    
    public String getSpeechData() {
        return speechData;
    }

    public void setSpeechData(String speechData) {
        this.speechData = speechData;
    }
}
