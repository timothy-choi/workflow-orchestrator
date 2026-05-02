package com.tim.workflow.orchestrator.dto;

public class WorkflowDetailResponse {

    private Long id;
    private String name;
    private String description;
    private Integer currentVersion;
    private String definitionJson;

    public WorkflowDetailResponse(
            Long id,
            String name,
            String description,
            Integer currentVersion,
            String definitionJson
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.currentVersion = currentVersion;
        this.definitionJson = definitionJson;
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

    public String getDefinitionJson() {
        return definitionJson;
    }
}