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

    @Column(nullable = false)
    private Integer attempt = 1;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 0;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "k8s_job_name")
    private String k8sJobName;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds = 300;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

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

    public Integer getAttempt() {
        return attempt;
    }

    public StepExecution setAttempt(Integer attempt) {
        this.attempt = attempt;
        return this;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public StepExecution setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public StepExecution setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public StepExecution setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        return this;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public StepExecution setFailureReason(String failureReason) {
        this.failureReason = failureReason;
        return this;
    }

    public String getK8sJobName() {
        return k8sJobName;
    }

    public StepExecution setK8sJobName(String k8sJobName) {
        this.k8sJobName = k8sJobName;
        return this;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public StepExecution setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public StepExecution setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public StepExecution setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
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
