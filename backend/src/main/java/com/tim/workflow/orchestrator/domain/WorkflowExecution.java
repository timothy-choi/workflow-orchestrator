package com.tim.workflow.orchestrator.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflow_executions")
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "workflow_version_id", nullable = false)
    private Long workflowVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowExecutionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "pause_requested", nullable = false)
    private boolean pauseRequested;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public Long getId() {
        return id;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public WorkflowExecution setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
        return this;
    }

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public WorkflowExecution setWorkflowVersionId(Long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
        return this;
    }

    public WorkflowExecutionStatus getStatus() {
        return status;
    }

    public WorkflowExecution setStatus(WorkflowExecutionStatus status) {
        this.status = status;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public WorkflowExecution setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public WorkflowExecution setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public WorkflowExecution setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public WorkflowExecution setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
        return this;
    }

    public boolean isPauseRequested() {
        return pauseRequested;
    }

    public WorkflowExecution setPauseRequested(boolean pauseRequested) {
        this.pauseRequested = pauseRequested;
        return this;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public WorkflowExecution setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
        return this;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public WorkflowExecution setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
        return this;
    }
}
