package com.marchina.model;

public class ChatResponse {
    private String response;
    private String audioData;
    private Boolean requirementsGathered;
    private final Long projectId;


    public ChatResponse(String response, String audioData, 
        Boolean requirementsGathered, Long projectId) {
        this.response = response;
        this.audioData = audioData;
        this.requirementsGathered = requirementsGathered;
        this.projectId = projectId;
        
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getAudioData() {
        return audioData;
    }

    public void setAudioData(String audioData) {
        this.audioData = audioData;
    }

    public Boolean getRequirementsGathered() {
        return requirementsGathered;
    }

    public void setRequirementsGathered(Boolean requirementsGathered) {
        this.requirementsGathered = requirementsGathered;
    }

    public Long getProjectId() {
        return projectId;
    }

}