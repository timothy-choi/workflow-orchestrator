package com.tim.workflow.orchestrator.dto;

public class WorkflowResponse {

    private Long id;
    private String name;
    private String description;
    private Integer currentVersion;

    public WorkflowResponse(Long id, String name, String description, Integer currentVersion) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.currentVersion = currentVersion;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }
}