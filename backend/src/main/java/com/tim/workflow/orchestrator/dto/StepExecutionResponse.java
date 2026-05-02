package com.tim.workflow.orchestrator.dto;

import java.time.Instant;

import com.tim.workflow.orchestrator.domain.StepExecutionStatus;

public class StepExecutionResponse {

    private final Long id;
    private final Integer stepIndex;
    private final String stepName;
    private final StepExecutionStatus status;
    private final Integer attempt;
    private final Integer maxRetries;
    private final Integer retryCount;
    private final Integer timeoutSeconds;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    public StepExecutionResponse(
            Long id,
            Integer stepIndex,
            String stepName,
            StepExecutionStatus status,
            Integer attempt,
            Integer maxRetries,
            Integer retryCount,
            Integer timeoutSeconds,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.stepIndex = stepIndex;
        this.stepName = stepName;
        this.status = status;
        this.attempt = attempt;
        this.maxRetries = maxRetries;
        this.retryCount = retryCount;
        this.timeoutSeconds = timeoutSeconds;
        this.failureReason = failureReason;
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

    public Integer getAttempt() {
        return attempt;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
