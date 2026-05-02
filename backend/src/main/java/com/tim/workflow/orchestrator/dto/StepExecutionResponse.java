package com.tim.workflow.orchestrator.dto;

import java.time.Instant;

import com.tim.workflow.orchestrator.domain.StepExecutionStatus;

public class StepExecutionResponse {

    private final Long id;
    private final Integer stepIndex;
    private final String stepName;
    private final StepExecutionStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public StepExecutionResponse(
            Long id,
            Integer stepIndex,
            String stepName,
            StepExecutionStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.stepIndex = stepIndex;
        this.stepName = stepName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public String getStepName() {
        return stepName;
    }

    public StepExecutionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
