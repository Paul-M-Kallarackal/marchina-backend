package com.marchina.model;

import java.time.LocalDateTime;

public class Diagram {
    private Long id;
    private Long projectId;
    private String name;
    private String type;
    private String content;

    // Default constructor
    public Diagram() {}

    // Constructor with fields
    public Diagram(Long id, Long projectId, String name, String type, String content, LocalDateTime createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.type = type;
        this.content = content;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }


} 