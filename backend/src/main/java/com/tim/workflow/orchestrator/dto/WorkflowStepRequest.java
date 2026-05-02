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

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies != null ? dependencies : List.of();
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds != null ? timeoutSeconds : 60;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries != null ? maxRetries : 0;
    }
}
