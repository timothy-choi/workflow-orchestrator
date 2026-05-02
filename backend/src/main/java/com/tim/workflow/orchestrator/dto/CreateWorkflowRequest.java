package com.tim.workflow.orchestrator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

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

    public String getDescription() {
        return description;
    }

    public List<WorkflowStepRequest> getSteps() {
        return steps;
    }
}