package com.tim.workflow.orchestrator.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public class WorkflowStepRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String image;

    @NotBlank
    private String command;

    private List<String> dependencies = List.of();

    private Integer timeoutSeconds = 60;

    private Integer maxRetries = 0;

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }
}