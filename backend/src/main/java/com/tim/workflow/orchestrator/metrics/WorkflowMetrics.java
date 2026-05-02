package com.tim.workflow.orchestrator.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class WorkflowMetrics {

    private final MeterRegistry registry;
    private final Timer schedulerLoopTimer;

    public WorkflowMetrics(MeterRegistry registry, WorkflowExecutionRepository workflowExecutionRepository) {
        this.registry = registry;
        this.schedulerLoopTimer = Timer.builder("scheduler.loop.duration")
                .description("Wall-clock duration of one scheduler tick over all active executions")
                .publishPercentileHistogram()
                .register(registry);

        Gauge.builder("active.workflow.executions", workflowExecutionRepository,
                repo -> repo.countByStatusIn(List.of(
                        WorkflowExecutionStatus.CREATED,
                        WorkflowExecutionStatus.RUNNING,
                        WorkflowExecutionStatus.PAUSED)))
                .description("Executions in CREATED, RUNNING, or PAUSED")
                .register(registry);
    }

    public Timer.Sample startSchedulerLoopSample() {
        return Timer.start(registry);
    }

    public void stopSchedulerLoopSample(Timer.Sample sample) {
        sample.stop(schedulerLoopTimer);
    }

    public void recordWorkflowTerminal(long workflowId, WorkflowExecutionStatus finalStatus, Instant createdAt, Instant finishedAt) {
        Counter.builder("workflow.executions.total")
                .tag("workflowId", String.valueOf(workflowId))
                .tag("finalStatus", finalStatus.name())
                .register(registry)
                .increment();
        if (createdAt != null && finishedAt != null && !finishedAt.isBefore(createdAt)) {
            Timer.builder("workflow.execution.duration")
                    .tag("workflowId", String.valueOf(workflowId))
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Duration.between(createdAt, finishedAt));
        }
    }

    /**
     * Terminal step outcomes: SUCCESS, FAILED, CANCELLED.
     */
    public void recordStepTerminal(String stepName, String status, Instant startedAt, Instant finishedAt) {
        Counter.builder("step.executions.total")
                .tag("stepName", safeTag(stepName))
                .tag("status", status)
                .register(registry)
                .increment();
        if (startedAt != null && finishedAt != null && !finishedAt.isBefore(startedAt)) {
            Timer.builder("step.execution.duration")
                    .tag("stepName", safeTag(stepName))
                    .tag("status", status)
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Duration.between(startedAt, finishedAt));
        }
    }

    /**
     * Records a non-terminal retry wait transition (timeout/callback failure with retries remaining).
     */
    public void recordStepRetryWaitDuration(String stepName, Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            return;
        }
        Timer.builder("step.execution.duration")
                .tag("stepName", safeTag(stepName))
                .tag("status", "RETRY_WAIT")
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.between(startedAt, finishedAt));
    }

    public void recordStepRetry(String stepName) {
        Counter.builder("step.retries.total")
                .tag("stepName", safeTag(stepName))
                .register(registry)
                .increment();
    }

    public void recordKubernetesJobCreateFailure() {
        Counter.builder("kubernetes.job.create.failures.total")
                .register(registry)
                .increment();
    }

    public void recordCallbackReceived(String status) {
        Counter.builder("callbacks.received.total")
                .tag("status", safeTag(status))
                .register(registry)
                .increment();
    }

    public void recordCallbackIgnored(String reason) {
        Counter.builder("callbacks.ignored.total")
                .tag("reason", safeTag(reason))
                .register(registry)
                .increment();
    }

    private static String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}
