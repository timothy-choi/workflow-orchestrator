package com.tim.workflow.orchestrator.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.tim.workflow.orchestrator.repository.ExecutionEventRepository;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;

@Service
public class StepCallbackService {

    private final OrchestratorProperties orchestratorProperties;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ExecutionEventRepository executionEventRepository;
    private final StepRetryCoordinator stepRetryCoordinator;
    private final ObjectMapper objectMapper;

    public StepCallbackService(
            OrchestratorProperties orchestratorProperties,
            WorkflowExecutionRepository workflowExecutionRepository,
            StepExecutionRepository stepExecutionRepository,
            ExecutionEventRepository executionEventRepository,
            StepRetryCoordinator stepRetryCoordinator,
            ObjectMapper objectMapper
    ) {
        this.orchestratorProperties = orchestratorProperties;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionEventRepository = executionEventRepository;
        this.stepRetryCoordinator = stepRetryCoordinator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleStepResult(StepResultRequest body, String callbackToken) {
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
        if (execution.getStatus() == WorkflowExecutionStatus.CREATED) {
            execution.setStatus(WorkflowExecutionStatus.RUNNING).setUpdatedAt(now);
            workflowExecutionRepository.save(execution);
        }

        if (body.getStatus() == StepResultStatus.SUCCESS) {
            if (step.getStatus() == StepExecutionStatus.SUCCESS) {
                return;
            }
            if (step.getStatus() != StepExecutionStatus.RUNNING) {
                return;
            }
            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(execution.getId())
                    .setEventType(ExecutionEventType.CALLBACK_RECEIVED)
                    .setPayload(callbackReceivedPayload(step.getStepName(), body))
                    .setCreatedAt(now));
            completeStepSuccess(execution, step, body, now);
            return;
        }

        // FAILED callback
        if (step.getStatus() != StepExecutionStatus.RUNNING) {
            return;
        }
        stepRetryCoordinator.handleFailureFromCallback(execution, step, body.getMessage(), now);
    }

    /**
     * Reconciler path when Kubernetes reports Job success but HTTP callback never arrived.
     */
    /**
     * Best-effort completion when the Job succeeded in Kubernetes but no HTTP callback arrived.
     * Uses a regular load (no execution pessimistic lock) so callers such as the reconciler can hold locks safely.
     */
    @Transactional
    public void applyReconcilerJobSucceeded(Long executionId, Long stepExecutionId, String message, Instant now) {
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

        WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution not found: " + executionId));

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
        step.setStatus(StepExecutionStatus.SUCCESS)
                .setFinishedAt(now)
                .setUpdatedAt(now)
                .setFailureReason(null);
        stepExecutionRepository.save(step);

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
        if (exec.getStatus() != WorkflowExecutionStatus.RUNNING) {
            return;
        }

        List<StepExecution> steps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        boolean allSuccess = !steps.isEmpty()
                && steps.stream().allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);

        if (allSuccess) {
            exec.setStatus(WorkflowExecutionStatus.SUCCEEDED)
                    .setFinishedAt(now)
                    .setUpdatedAt(now);
            workflowExecutionRepository.save(exec);

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
