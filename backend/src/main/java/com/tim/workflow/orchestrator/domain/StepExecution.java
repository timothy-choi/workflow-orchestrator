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
@Table(name = "step_executions")
public class StepExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_execution_id", nullable = false)
    private Long workflowExecutionId;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StepExecutionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getWorkflowExecutionId() {
        return workflowExecutionId;
    }

    public StepExecution setWorkflowExecutionId(Long workflowExecutionId) {
        this.workflowExecutionId = workflowExecutionId;
        return this;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public StepExecution setStepIndex(Integer stepIndex) {
        this.stepIndex = stepIndex;
        return this;
    }

    public String getStepName() {
        return stepName;
    }

    public StepExecution setStepName(String stepName) {
        this.stepName = stepName;
        return this;
    }

    public StepExecutionStatus getStatus() {
        return status;
    }

    public StepExecution setStatus(StepExecutionStatus status) {
        this.status = status;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public StepExecution setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public StepExecution setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
