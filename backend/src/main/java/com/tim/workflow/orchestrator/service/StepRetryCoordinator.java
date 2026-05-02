package com.tim.workflow.orchestrator.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.domain.ExecutionEvent;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.logging.WorkflowLogContext;
import com.tim.workflow.orchestrator.metrics.WorkflowMetrics;
import com.tim.workflow.orchestrator.repository.ExecutionEventRepository;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;

@Service
public class StepRetryCoordinator {

    private static final Logger log = LoggerFactory.getLogger(StepRetryCoordinator.class);

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ExecutionEventRepository executionEventRepository;
    private final ObjectMapper objectMapper;

    private final int retryBackoffBaseSeconds;

    private final WorkflowMetrics workflowMetrics;

    public StepRetryCoordinator(
            WorkflowExecutionRepository workflowExecutionRepository,
            StepExecutionRepository stepExecutionRepository,
            ExecutionEventRepository executionEventRepository,
            ObjectMapper objectMapper,
            @Value("${workflow.scheduler.retry-backoff-base-seconds:10}") int retryBackoffBaseSeconds,
            WorkflowMetrics workflowMetrics
    ) {
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionEventRepository = executionEventRepository;
        this.objectMapper = objectMapper;
        this.retryBackoffBaseSeconds = retryBackoffBaseSeconds;
        this.workflowMetrics = workflowMetrics;
    }

    /**
     * Handles callback-like failures (HTTP FAILED). Emits {@link ExecutionEventType#STEP_RETRY_SCHEDULED}
     * or {@link ExecutionEventType#STEP_FAILED}.
     */
    @Transactional
    public void handleFailureFromCallback(WorkflowExecution execution, StepExecution step, String failureReason, Instant now) {
        handleFailure(execution, step, failureReason, null, now);
    }

    /**
     * Handles failures where an optional diagnostic event should be logged first (timeouts, reconciler).
     */
    @Transactional
    public void handleFailureWithDiagnostic(
            WorkflowExecution execution,
            StepExecution step,
            String failureReason,
            ExecutionEventType diagnosticEvent,
            Instant now
    ) {
        handleFailure(execution, step, failureReason, diagnosticEvent, now);
    }

    private void handleFailure(
            WorkflowExecution execution,
            StepExecution step,
            String failureReason,
            ExecutionEventType diagnosticEvent,
            Instant now
    ) {
        if (diagnosticEvent != null) {
            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(execution.getId())
                    .setEventType(diagnosticEvent)
                    .setPayload(failurePayload(step.getStepName(), failureReason))
                    .setCreatedAt(now));
        }

        if (step.getRetryCount() < step.getMaxRetries()) {
            Instant attemptStart = step.getStartedAt();
            int newRetryCount = step.getRetryCount() + 1;
            step.setRetryCount(newRetryCount)
                    .setAttempt(step.getAttempt() + 1)
                    .setStatus(StepExecutionStatus.RETRY_WAIT)
                    .setFailureReason(failureReason)
                    .setNextRetryAt(now.plusSeconds(computeBackoffSeconds(newRetryCount)))
                    .setK8sJobName(null)
                    .setStartedAt(null)
                    .setUpdatedAt(now);
            stepExecutionRepository.save(step);

            workflowMetrics.recordStepRetry(step.getStepName());
            workflowMetrics.recordStepRetryWaitDuration(step.getStepName(), attemptStart, now);

            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(execution.getId())
                    .setEventType(ExecutionEventType.STEP_RETRY_SCHEDULED)
                    .setPayload(retryScheduledPayload(step.getStepName(), newRetryCount, step.getNextRetryAt()))
                    .setCreatedAt(now));

            WorkflowLogContext.put(execution.getId(), step.getId(), execution.getWorkflowId(), null, "STEP_RETRY_SCHEDULED");
            try {
                log.info("Retry scheduled stepName={} retryCount={} nextRetryAt={}", step.getStepName(), newRetryCount, step.getNextRetryAt());
            } finally {
                WorkflowLogContext.clear();
            }
        } else {
            step.setStatus(StepExecutionStatus.FAILED)
                    .setFailureReason(failureReason)
                    .setFinishedAt(now)
                    .setUpdatedAt(now);
            stepExecutionRepository.save(step);

            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(execution.getId())
                    .setEventType(ExecutionEventType.STEP_FAILED)
                    .setPayload(failurePayload(step.getStepName(), failureReason))
                    .setCreatedAt(now));

            workflowMetrics.recordStepTerminal(step.getStepName(), "FAILED", step.getStartedAt(), now);

            if (execution.getStatus() == WorkflowExecutionStatus.RUNNING
                    || execution.getStatus() == WorkflowExecutionStatus.PAUSED) {
                execution.setStatus(WorkflowExecutionStatus.FAILED)
                        .setFinishedAt(now)
                        .setUpdatedAt(now)
                        .setPausedAt(null);
                workflowExecutionRepository.save(execution);

                executionEventRepository.save(new ExecutionEvent()
                        .setWorkflowExecutionId(execution.getId())
                        .setEventType(ExecutionEventType.EXECUTION_FAILED)
                        .setPayload(executionFailedPayload(step.getStepName(), failureReason))
                        .setCreatedAt(now));

                WorkflowExecution reloaded = workflowExecutionRepository.findById(execution.getId()).orElse(execution);
                workflowMetrics.recordWorkflowTerminal(
                        reloaded.getWorkflowId(),
                        WorkflowExecutionStatus.FAILED,
                        reloaded.getCreatedAt(),
                        now);

                WorkflowLogContext.put(execution.getId(), step.getId(), reloaded.getWorkflowId(), null, "EXECUTION_FAILED");
                try {
                    log.warn("Execution failed after step exhausted retries stepName={}", step.getStepName());
                } finally {
                    WorkflowLogContext.clear();
                }
            }
        }
    }

    private long computeBackoffSeconds(int retryCountAfterIncrement) {
        int exp = Math.min(Math.max(retryCountAfterIncrement - 1, 0), 20);
        return retryBackoffBaseSeconds * (1L << exp);
    }

    private String failurePayload(String stepName, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepName", stepName);
        map.put("reason", reason);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String executionFailedPayload(String stepName, String failureReason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepName", stepName);
        if (failureReason != null && !failureReason.isBlank()) {
            map.put("failureReason", failureReason);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String retryScheduledPayload(String stepName, int retryCount, Instant nextRetryAt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepName", stepName);
        map.put("retryCount", retryCount);
        map.put("nextRetryAt", nextRetryAt.toString());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
