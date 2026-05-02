package com.tim.workflow.orchestrator.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public class CreateWorkflowRequest {

    @NotBlank
    private String name;

    private String description;

    @NotEmpty
    @Valid
    private List<WorkflowStepRequest> steps;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<WorkflowStepRequest> getSteps() {
        return steps;
    }

    public void setSteps(List<WorkflowStepRequest> steps) {
        this.steps = steps;
    }
}
