package com.tim.workflow.orchestrator.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.api.internal.StepCallbackController;
import com.tim.workflow.orchestrator.config.OrchestratorProperties;
import com.tim.workflow.orchestrator.domain.ExecutionEvent;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.StepResultRequest;
import com.tim.workflow.orchestrator.dto.StepResultRequest.StepResultStatus;
import com.tim.workflow.orchestrator.logging.WorkflowLogContext;
import com.tim.workflow.orchestrator.metrics.WorkflowMetrics;
import com.tim.workflow.orchestrator.repository.ExecutionEventRepository;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;

@Service
public class StepCallbackService {

    private static final Logger log = LoggerFactory.getLogger(StepCallbackService.class);

    private final OrchestratorProperties orchestratorProperties;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ExecutionEventRepository executionEventRepository;
    private final StepRetryCoordinator stepRetryCoordinator;
    private final ObjectMapper objectMapper;
    private final WorkflowMetrics workflowMetrics;

    public StepCallbackService(
            OrchestratorProperties orchestratorProperties,
            WorkflowExecutionRepository workflowExecutionRepository,
            StepExecutionRepository stepExecutionRepository,
            ExecutionEventRepository executionEventRepository,
            StepRetryCoordinator stepRetryCoordinator,
            ObjectMapper objectMapper,
            WorkflowMetrics workflowMetrics
    ) {
        this.orchestratorProperties = orchestratorProperties;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionEventRepository = executionEventRepository;
        this.stepRetryCoordinator = stepRetryCoordinator;
        this.objectMapper = objectMapper;
        this.workflowMetrics = workflowMetrics;
    }

    @Transactional
    public StepCallbackOutcome handleStepResult(StepResultRequest body, String callbackToken) {
        String expected = orchestratorProperties.getCallback().getToken();
        if (callbackToken == null || !expected.equals(callbackToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing "
                    + StepCallbackController.CALLBACK_TOKEN_HEADER);
        }

        WorkflowExecution execution = workflowExecutionRepository.findLockedById(body.getExecutionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found"));

        StepExecution step = stepExecutionRepository.findById(body.getStepExecutionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step execution not found"));

        if (!step.getWorkflowExecutionId().equals(execution.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stepExecutionId does not belong to execution");
        }

        Instant now = Instant.now();

        if (execution.getStatus() == WorkflowExecutionStatus.CANCELLED || execution.isCancelRequested()) {
            recordIgnoredCallback(execution.getId(), ExecutionEventType.CALLBACK_IGNORED_EXECUTION_CANCELLED,
                    step.getStepName(), body, now);
            return StepCallbackOutcome.IGNORED_CANCELLED;
        }

        if (step.getStatus() == StepExecutionStatus.CANCELLED) {
            recordIgnoredCallback(execution.getId(), ExecutionEventType.CALLBACK_IGNORED_STEP_CANCELLED,
                    step.getStepName(), body, now);
            return StepCallbackOutcome.IGNORED_CANCELLED;
        }

        if (execution.getStatus() == WorkflowExecutionStatus.CREATED) {
            execution.setStatus(WorkflowExecutionStatus.RUNNING).setUpdatedAt(now);
            workflowExecutionRepository.save(execution);
        }

        if (body.getStatus() == StepResultStatus.SUCCESS) {
            if (step.getStatus() == StepExecutionStatus.SUCCESS) {
                return StepCallbackOutcome.ACCEPTED;
            }
            if (step.getStatus() != StepExecutionStatus.RUNNING) {
                return StepCallbackOutcome.ACCEPTED;
            }
            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(execution.getId())
                    .setEventType(ExecutionEventType.CALLBACK_RECEIVED)
                    .setPayload(callbackReceivedPayload(step.getStepName(), body))
                    .setCreatedAt(now));
            workflowMetrics.recordCallbackReceived("SUCCESS");
            WorkflowLogContext.put(execution.getId(), step.getId(), execution.getWorkflowId(), step.getK8sJobName(), "CALLBACK_RECEIVED");
            try {
                log.info("Callback received stepName={} status=SUCCESS", step.getStepName());
            } finally {
                WorkflowLogContext.clear();
            }
            completeStepSuccess(execution, step, body, now);
            return StepCallbackOutcome.ACCEPTED;
        }

        // FAILED callback
        if (step.getStatus() != StepExecutionStatus.RUNNING) {
            return StepCallbackOutcome.ACCEPTED;
        }
        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(execution.getId())
                .setEventType(ExecutionEventType.CALLBACK_RECEIVED)
                .setPayload(callbackReceivedPayload(step.getStepName(), body))
                .setCreatedAt(now));
        workflowMetrics.recordCallbackReceived("FAILED");
        WorkflowLogContext.put(execution.getId(), step.getId(), execution.getWorkflowId(), step.getK8sJobName(), "CALLBACK_RECEIVED");
        try {
            log.info("Callback received stepName={} status=FAILED", step.getStepName());
        } finally {
            WorkflowLogContext.clear();
        }
        stepRetryCoordinator.handleFailureFromCallback(execution, step, body.getMessage(), now);
        return StepCallbackOutcome.ACCEPTED;
    }

    private void recordIgnoredCallback(
            Long executionId,
            ExecutionEventType type,
            String stepName,
            StepResultRequest body,
            Instant now
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepName", stepName);
        map.put("callbackStatus", body.getStatus().name());
        map.put("message", body.getMessage());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(executionId)
                .setEventType(type)
                .setPayload(payload)
                .setCreatedAt(now));
    }

    /**
     * Best-effort completion when the Job succeeded in Kubernetes but no HTTP callback arrived.
     */
    @Transactional
    public void applyReconcilerJobSucceeded(Long executionId, Long stepExecutionId, String message, Instant now) {
        WorkflowExecution execution = workflowExecutionRepository.findById(executionId).orElse(null);
        if (execution == null
                || execution.getStatus() == WorkflowExecutionStatus.CANCELLED
                || execution.isCancelRequested()) {
            return;
        }

        StepExecution step = stepExecutionRepository.findById(stepExecutionId)
                .orElseThrow(() -> new IllegalStateException("Step not found: " + stepExecutionId));
        if (!step.getWorkflowExecutionId().equals(executionId)) {
            throw new IllegalStateException("Step does not belong to execution");
        }
        if (step.getStatus() == StepExecutionStatus.SUCCESS) {
            return;
        }
        if (step.getStatus() != StepExecutionStatus.RUNNING) {
            return;
        }

        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(executionId)
                .setEventType(ExecutionEventType.CALLBACK_MISSING_JOB_SUCCEEDED)
                .setPayload(stepOutcomePayload(step.getStepName(), message))
                .setCreatedAt(now));

        StepResultRequest synthetic = new StepResultRequest();
        synthetic.setStatus(StepResultStatus.SUCCESS);
        synthetic.setMessage(message);
        completeStepSuccess(execution, step, synthetic, now);
    }

    private void completeStepSuccess(
            WorkflowExecution execution,
            StepExecution step,
            StepResultRequest body,
            Instant now
    ) {
        Instant startedAtSnapshot = step.getStartedAt();
        step.setStatus(StepExecutionStatus.SUCCESS)
                .setFinishedAt(now)
                .setUpdatedAt(now)
                .setFailureReason(null);
        stepExecutionRepository.save(step);

        workflowMetrics.recordStepSucceeded(startedAtSnapshot, now);

        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(execution.getId())
                .setEventType(ExecutionEventType.STEP_SUCCEEDED)
                .setPayload(stepOutcomePayload(step.getStepName(), body))
                .setCreatedAt(now));

        tryCompleteSucceededExecution(execution.getId(), now);
    }

    private String callbackReceivedPayload(String stepName, StepResultRequest body) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepName", stepName);
        map.put("status", body.getStatus().name());
        map.put("message", body.getMessage());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void tryCompleteSucceededExecution(Long executionId, Instant now) {
        workflowExecutionRepository.flush();
        WorkflowExecution exec = workflowExecutionRepository.findById(executionId).orElseThrow();
        if (exec.getStatus() != WorkflowExecutionStatus.RUNNING || exec.isPauseRequested()) {
            return;
        }

        List<StepExecution> steps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        boolean allSuccess = !steps.isEmpty()
                && steps.stream().allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);

        if (allSuccess) {
            exec.setStatus(WorkflowExecutionStatus.SUCCEEDED)
                    .setFinishedAt(now)
                    .setUpdatedAt(now)
                    .setPausedAt(null)
                    .setPauseRequested(false);
            workflowExecutionRepository.save(exec);

            workflowMetrics.recordExecutionSucceeded(exec.getCreatedAt(), now);

            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(executionId)
                    .setEventType(ExecutionEventType.EXECUTION_SUCCEEDED)
                    .setPayload("{}")
                    .setCreatedAt(now));
        }
    }

    private String stepOutcomePayload(String stepName, StepResultRequest body) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepName", stepName);
        map.put("message", body.getMessage());
        map.put("status", body.getStatus().name());
        if (body.getLogsRef() != null) {
            map.put("logsRef", body.getLogsRef());
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize callback payload", e);
        }
    }

    private String stepOutcomePayload(String stepName, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepName", stepName);
        map.put("message", message);
        map.put("status", StepResultStatus.SUCCESS.name());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
