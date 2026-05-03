package com.tim.workflow.orchestrator.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Workflow metrics for Micrometer / Prometheus. Logical Micrometer names use underscores; Prometheus
 * scrape names follow Micrometer + Prometheus naming rules (for example the executions-created counter
 * is exposed as {@code workflow_executions_total}, and current-state gauges use a {@code _current}
 * suffix where needed to avoid type clashes with counters).
 */
@Component
public class WorkflowMetrics {

    private final MeterRegistry registry;

    private final Counter executionsCreated;
    private final Counter executionsSucceeded;
    private final Counter executionsFailed;
    private final Counter executionsCancelled;
    private final Counter executionsPaused;
    private final Counter executionsResumed;

    private final Counter stepsStarted;
    private final Counter stepsSucceeded;
    private final Counter stepsFailed;

    private final Counter stepsRetried;

    private final Counter kubernetesJobsCreated;
    private final Counter kubernetesJobsDeleted;

    private final Counter manualRetriesRequested;

    private final Timer executionDuration;
    private final Timer stepDuration;

    public WorkflowMetrics(
            MeterRegistry registry,
            WorkflowExecutionRepository workflowExecutionRepository,
            StepExecutionRepository stepExecutionRepository
    ) {
        this.registry = registry;

        this.executionsCreated = Counter.builder("workflow_executions_created")
                .description("Workflow executions persisted after successful creation")
                .register(registry);
        this.executionsSucceeded = Counter.builder("workflow_executions_succeeded")
                .description("Executions transitioned to SUCCEEDED")
                .register(registry);
        this.executionsFailed = Counter.builder("workflow_executions_failed")
                .description("Executions transitioned to FAILED")
                .register(registry);
        this.executionsCancelled = Counter.builder("workflow_executions_cancelled")
                .description("Executions transitioned to CANCELLED")
                .register(registry);
        this.executionsPaused = Counter.builder("workflow_executions_paused")
                .description("Executions transitioned to PAUSED")
                .register(registry);
        this.executionsResumed = Counter.builder("workflow_executions_resumed")
                .description("Executions resumed from PAUSED to RUNNING")
                .register(registry);

        this.stepsStarted = Counter.builder("workflow_steps_started")
                .description("Steps claimed PENDING to RUNNING for dispatch")
                .register(registry);
        this.stepsSucceeded = Counter.builder("workflow_steps_succeeded")
                .description("Steps transitioned to SUCCESS")
                .register(registry);
        this.stepsFailed = Counter.builder("workflow_steps_failed")
                .description("Steps transitioned to FAILED (terminal)")
                .register(registry);

        this.stepsRetried = Counter.builder("workflow_steps_retried")
                .description("Automatic step retries scheduled (backoff retry wait)")
                .register(registry);

        this.kubernetesJobsCreated = Counter.builder("workflow_kubernetes_jobs_created")
                .description("Kubernetes Jobs successfully created for steps")
                .register(registry);
        this.kubernetesJobsDeleted = Counter.builder("workflow_kubernetes_jobs_deleted")
                .description("Kubernetes Jobs successfully deleted")
                .register(registry);

        this.manualRetriesRequested = Counter.builder("workflow_manual_retries_requested")
                .description("Manual retries requested for FAILED steps")
                .register(registry);

        this.executionDuration = Timer.builder("workflow_execution_duration")
                .description("Wall time from execution creation to terminal outcome")
                .publishPercentileHistogram()
                .register(registry);
        this.stepDuration = Timer.builder("workflow_step_duration")
                .description("Wall time from step start to terminal outcome")
                .publishPercentileHistogram()
                .register(registry);

        Gauge.builder("workflow_executions_running", workflowExecutionRepository,
                        r -> r.countByStatusIn(List.of(WorkflowExecutionStatus.RUNNING)))
                .description("Executions currently RUNNING")
                .register(registry);
        Gauge.builder("workflow.executions.paused.current", workflowExecutionRepository,
                        r -> r.countByStatusIn(List.of(WorkflowExecutionStatus.PAUSED)))
                .description("Executions currently PAUSED")
                .register(registry);

        Gauge.builder("workflow_steps_running", stepExecutionRepository,
                        s -> s.countByStatus(StepExecutionStatus.RUNNING))
                .description("Steps currently RUNNING")
                .register(registry);
        Gauge.builder("workflow_steps_pending", stepExecutionRepository,
                        s -> s.countByStatus(StepExecutionStatus.PENDING))
                .description("Steps currently PENDING")
                .register(registry);
        Gauge.builder("workflow.steps.failed.current", stepExecutionRepository,
                        s -> s.countByStatus(StepExecutionStatus.FAILED))
                .description("Steps currently FAILED")
                .register(registry);
    }

    public void recordExecutionCreated() {
        executionsCreated.increment();
    }

    public void recordExecutionPaused() {
        executionsPaused.increment();
    }

    public void recordExecutionResumed() {
        executionsResumed.increment();
    }

    public void recordExecutionSucceeded(Instant createdAt, Instant finishedAt) {
        executionsSucceeded.increment();
        recordExecutionDuration(createdAt, finishedAt);
    }

    public void recordExecutionFailed(Instant createdAt, Instant finishedAt) {
        executionsFailed.increment();
        recordExecutionDuration(createdAt, finishedAt);
    }

    public void recordExecutionCancelled(Instant createdAt, Instant finishedAt) {
        executionsCancelled.increment();
        recordExecutionDuration(createdAt, finishedAt);
    }

    private void recordExecutionDuration(Instant createdAt, Instant finishedAt) {
        if (createdAt != null && finishedAt != null && !finishedAt.isBefore(createdAt)) {
            executionDuration.record(Duration.between(createdAt, finishedAt));
        }
    }

    public void recordStepStarted() {
        stepsStarted.increment();
    }

    public void recordStepSucceeded(Instant startedAt, Instant finishedAt) {
        stepsSucceeded.increment();
        recordStepDuration(startedAt, finishedAt);
    }

    public void recordStepFailed(Instant startedAt, Instant finishedAt) {
        stepsFailed.increment();
        recordStepDuration(startedAt, finishedAt);
    }

    /**
     * Terminal step outcome without succeeded/failed counters (e.g. CANCELLED).
     */
    public void recordStepTerminalDuration(Instant startedAt, Instant finishedAt) {
        recordStepDuration(startedAt, finishedAt);
    }

    private void recordStepDuration(Instant startedAt, Instant finishedAt) {
        if (startedAt != null && finishedAt != null && !finishedAt.isBefore(startedAt)) {
            stepDuration.record(Duration.between(startedAt, finishedAt));
        }
    }

    public void recordAutomaticStepRetryScheduled() {
        stepsRetried.increment();
    }

    public void recordCallbackReceived(String callbackStatus) {
        Counter.builder("workflow_callbacks_received")
                .tag("status", safeTag(callbackStatus))
                .register(registry)
                .increment();
    }

    public void recordKubernetesJobCreated() {
        kubernetesJobsCreated.increment();
    }

    public void recordKubernetesJobDeleted() {
        kubernetesJobsDeleted.increment();
    }

    public void recordKubernetesJobCreateFailure() {
        Counter.builder("workflow_kubernetes_jobs_create_failed")
                .description("Kubernetes Job create API failures")
                .register(registry)
                .increment();
    }

    public void recordManualRetryRequested() {
        manualRetriesRequested.increment();
    }

    private static String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}
