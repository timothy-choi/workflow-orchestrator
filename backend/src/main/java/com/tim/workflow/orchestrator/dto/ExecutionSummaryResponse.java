package com.tim.workflow.orchestrator.dto;

import java.time.Instant;

import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;

public class ExecutionSummaryResponse {

    private final Long id;
    private final Long workflowId;
    private final WorkflowExecutionStatus status;
    private final Instant createdAt;
    private final Instant finishedAt;

    public ExecutionSummaryResponse(
            Long id,
            Long workflowId,
            WorkflowExecutionStatus status,
            Instant createdAt,
            Instant finishedAt
    ) {
        this.id = id;
        this.workflowId = workflowId;
        this.status = status;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public WorkflowExecutionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
