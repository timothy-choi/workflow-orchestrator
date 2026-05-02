package com.tim.workflow.orchestrator.dto;

import java.time.Instant;
import java.util.List;

import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;

public class ExecutionResponse {

    private final Long id;
    private final Long workflowId;
    private final Long workflowVersionId;
    private final WorkflowExecutionStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant finishedAt;
    private final Instant pausedAt;
    private final boolean cancelRequested;
    private final Instant cancelledAt;
    private final List<StepExecutionResponse> steps;
    private final List<ExecutionEventResponse> events;

    public ExecutionResponse(
            Long id,
            Long workflowId,
            Long workflowVersionId,
            WorkflowExecutionStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt,
            Instant pausedAt,
            boolean cancelRequested,
            Instant cancelledAt,
            List<StepExecutionResponse> steps,
            List<ExecutionEventResponse> events
    ) {
        this.id = id;
        this.workflowId = workflowId;
        this.workflowVersionId = workflowVersionId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.finishedAt = finishedAt;
        this.pausedAt = pausedAt;
        this.cancelRequested = cancelRequested;
        this.cancelledAt = cancelledAt;
        this.steps = steps;
        this.events = events;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public WorkflowExecutionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<StepExecutionResponse> getSteps() {
        return steps;
    }

    public List<ExecutionEventResponse> getEvents() {
        return events;
    }
}
