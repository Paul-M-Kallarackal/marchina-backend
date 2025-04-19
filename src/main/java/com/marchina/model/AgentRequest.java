package com.marchina.model;

public class AgentRequest {
    private String userRequest;

    public AgentRequest() {}

    public AgentRequest(String userRequest) {
        this.userRequest = userRequest;
    }

    public String getUserRequest() {
        return userRequest;
    }

    public void setUserRequest(String userRequest) {
        this.userRequest = userRequest;
    }
} 