package com.marchina.dto;

public class RequirementSet {
    private String erdRequirements;
    private String flowchartRequirements;
    private String sequenceDiagramRequirements;
    private String classDiagramRequirements;

    public RequirementSet() {
    }

    public String getErdRequirements() {
        return erdRequirements;
    }

    public void setErdRequirements(String erdRequirements) {
        this.erdRequirements = erdRequirements;
    }

    public String getFlowchartRequirements() {
        return flowchartRequirements;
    }

    public void setFlowchartRequirements(String flowchartRequirements) {
        this.flowchartRequirements = flowchartRequirements;
    }

    public String getSequenceDiagramRequirements() {
        return sequenceDiagramRequirements;
    }

    public void setSequenceDiagramRequirements(String sequenceDiagramRequirements) {
        this.sequenceDiagramRequirements = sequenceDiagramRequirements;
    }

    public String getClassDiagramRequirements() {
        return classDiagramRequirements;
    }

    public void setClassDiagramRequirements(String classDiagramRequirements) {
        this.classDiagramRequirements = classDiagramRequirements;
    }
} 