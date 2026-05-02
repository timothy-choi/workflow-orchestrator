package com.tim.workflow.orchestrator.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.config.ExecutionMode;
import com.tim.workflow.orchestrator.config.OrchestratorProperties;
import com.tim.workflow.orchestrator.domain.ExecutionEvent;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.Workflow;
import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowVersion;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.ExecutionEventResponse;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.StepExecutionResponse;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.k8s.KubernetesJobDeleter;
import com.tim.workflow.orchestrator.repository.ExecutionEventRepository;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowRepository;
import com.tim.workflow.orchestrator.repository.WorkflowVersionRepository;

@Service
public class ExecutionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ExecutionEventRepository executionEventRepository;
    private final ObjectMapper objectMapper;
    private final OrchestratorProperties orchestratorProperties;
    private final ObjectProvider<KubernetesJobDeleter> kubernetesJobDeleter;

    public ExecutionService(
            WorkflowRepository workflowRepository,
            WorkflowVersionRepository workflowVersionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            StepExecutionRepository stepExecutionRepository,
            ExecutionEventRepository executionEventRepository,
            ObjectMapper objectMapper,
            OrchestratorProperties orchestratorProperties,
            ObjectProvider<KubernetesJobDeleter> kubernetesJobDeleter
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionEventRepository = executionEventRepository;
        this.objectMapper = objectMapper;
        this.orchestratorProperties = orchestratorProperties;
        this.kubernetesJobDeleter = kubernetesJobDeleter;
    }

    @Transactional
    public ExecutionResponse createExecution(Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));

        WorkflowVersion version = workflowVersionRepository
                .findByWorkflowIdAndVersionNumber(workflow.getId(), workflow.getCurrentVersion())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Workflow version not found for workflow: " + workflowId
                ));

        CreateWorkflowRequest definition = parseDefinition(version.getDefinitionJson());
        List<WorkflowStepRequest> stepRequests = definition.getSteps();
        if (stepRequests == null || stepRequests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow definition has no steps");
        }

        Instant now = Instant.now();
        WorkflowExecution execution = new WorkflowExecution()
                .setWorkflowId(workflow.getId())
                .setWorkflowVersionId(version.getId())
                .setStatus(WorkflowExecutionStatus.CREATED)
                .setCancelRequested(false)
                .setCreatedAt(now)
                .setUpdatedAt(now);

        WorkflowExecution savedExecution = workflowExecutionRepository.save(execution);

        List<StepExecution> stepEntities = new ArrayList<>();
        for (int i = 0; i < stepRequests.size(); i++) {
            WorkflowStepRequest stepRequest = stepRequests.get(i);
            StepExecution stepExecution = new StepExecution()
                    .setWorkflowExecutionId(savedExecution.getId())
                    .setStepIndex(i)
                    .setStepName(stepRequest.getName())
                    .setStatus(StepExecutionStatus.PENDING)
                    .setAttempt(1)
                    .setRetryCount(0)
                    .setMaxRetries(stepRequest.getMaxRetries())
                    .setTimeoutSeconds(stepRequest.getTimeoutSeconds())
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            stepEntities.add(stepExecution);
        }
        stepExecutionRepository.saveAll(stepEntities);

        String eventPayload = buildCreationPayload(workflow.getId(), version.getId());
        ExecutionEvent createdEvent = new ExecutionEvent()
                .setWorkflowExecutionId(savedExecution.getId())
                .setEventType(ExecutionEventType.EXECUTION_CREATED)
                .setPayload(eventPayload)
                .setCreatedAt(now);
        executionEventRepository.save(createdEvent);

        List<StepExecution> persistedSteps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(savedExecution.getId());
        List<ExecutionEvent> persistedEvents =
                executionEventRepository.findByWorkflowExecutionIdOrderByCreatedAtAsc(savedExecution.getId());

        return toResponse(savedExecution, persistedSteps, persistedEvents);
    }

    @Transactional(readOnly = true)
    public ExecutionResponse getExecution(Long executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found: " + executionId));

        List<StepExecution> steps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        List<ExecutionEvent> events =
                executionEventRepository.findByWorkflowExecutionIdOrderByCreatedAtAsc(executionId);

        return toResponse(execution, steps, events);
    }

    @Transactional
    public ExecutionResponse pauseExecution(Long executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findLockedById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found: " + executionId));

        if (execution.getStatus() != WorkflowExecutionStatus.CREATED
                && execution.getStatus() != WorkflowExecutionStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Execution cannot be paused from status " + execution.getStatus());
        }

        Instant now = Instant.now();
        execution.setStatus(WorkflowExecutionStatus.PAUSED)
                .setPausedAt(now)
                .setUpdatedAt(now);
        workflowExecutionRepository.save(execution);

        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(executionId)
                .setEventType(ExecutionEventType.EXECUTION_PAUSED)
                .setPayload("{}")
                .setCreatedAt(now));

        return loadFullResponse(executionId);
    }

    @Transactional
    public ExecutionResponse resumeExecution(Long executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findLockedById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found: " + executionId));

        if (execution.getStatus() != WorkflowExecutionStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Execution cannot be resumed from status " + execution.getStatus());
        }

        Instant now = Instant.now();
        execution.setStatus(WorkflowExecutionStatus.RUNNING)
                .setPausedAt(null)
                .setUpdatedAt(now);
        workflowExecutionRepository.save(execution);

        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(executionId)
                .setEventType(ExecutionEventType.EXECUTION_RESUMED)
                .setPayload("{}")
                .setCreatedAt(now));

        return loadFullResponse(executionId);
    }

    @Transactional
    public ExecutionResponse cancelExecution(Long executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findLockedById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found: " + executionId));

        if (execution.getStatus() == WorkflowExecutionStatus.CANCELLED) {
            return loadFullResponse(executionId);
        }
        if (execution.getStatus() == WorkflowExecutionStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel a succeeded execution");
        }

        Instant now = Instant.now();
        execution.setCancelRequested(true)
                .setStatus(WorkflowExecutionStatus.CANCELLED)
                .setCancelledAt(now)
                .setFinishedAt(now)
                .setPausedAt(null)
                .setUpdatedAt(now);
        workflowExecutionRepository.save(execution);

        List<StepExecution> steps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        for (StepExecution step : steps) {
            if (step.getStatus() == StepExecutionStatus.PENDING
                    || step.getStatus() == StepExecutionStatus.RETRY_WAIT) {
                cancelStepRecord(step, now);
                executionEventRepository.save(stepCancelledEvent(executionId, step.getStepName(), now));
            } else if (step.getStatus() == StepExecutionStatus.RUNNING) {
                if (orchestratorProperties.getExecution().getMode() == ExecutionMode.KUBERNETES
                        && step.getK8sJobName() != null && !step.getK8sJobName().isBlank()) {
                    KubernetesJobDeleter deleter = kubernetesJobDeleter.getIfAvailable();
                    if (deleter != null) {
                        deleter.deleteJobIfPresent(step.getK8sJobName());
                    }
                }
                cancelStepRecord(step, now);
                executionEventRepository.save(stepCancelledEvent(executionId, step.getStepName(), now));
            }
        }

        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(executionId)
                .setEventType(ExecutionEventType.EXECUTION_CANCELLED)
                .setPayload("{}")
                .setCreatedAt(now));

        return loadFullResponse(executionId);
    }

    @Transactional
    public ExecutionResponse manualRetryFailedStep(Long stepExecutionId) {
        StepExecution step = stepExecutionRepository.findById(stepExecutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step execution not found"));

        if (step.getStatus() != StepExecutionStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Manual retry allowed only for FAILED steps; current status=" + step.getStatus());
        }

        WorkflowExecution execution = workflowExecutionRepository.findLockedById(step.getWorkflowExecutionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found"));

        if (execution.getStatus() == WorkflowExecutionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot retry step on a cancelled execution");
        }

        Instant now = Instant.now();
        step.setAttempt(step.getAttempt() + 1)
                .setRetryCount(step.getRetryCount() + 1)
                .setStatus(StepExecutionStatus.PENDING)
                .setFailureReason(null)
                .setNextRetryAt(null)
                .setK8sJobName(null)
                .setStartedAt(null)
                .setFinishedAt(null)
                .setUpdatedAt(now);
        stepExecutionRepository.save(step);

        if (execution.getStatus() == WorkflowExecutionStatus.FAILED) {
            execution.setStatus(WorkflowExecutionStatus.RUNNING)
                    .setFinishedAt(null)
                    .setUpdatedAt(now);
            workflowExecutionRepository.save(execution);
        }

        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(execution.getId())
                .setEventType(ExecutionEventType.STEP_MANUAL_RETRY_REQUESTED)
                .setPayload(stepPayloadJson(step.getStepName()))
                .setCreatedAt(now));

        return loadFullResponse(execution.getId());
    }

    private ExecutionResponse loadFullResponse(Long executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findById(executionId).orElseThrow();
        List<StepExecution> steps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        List<ExecutionEvent> events =
                executionEventRepository.findByWorkflowExecutionIdOrderByCreatedAtAsc(executionId);
        return toResponse(execution, steps, events);
    }

    private void cancelStepRecord(StepExecution step, Instant now) {
        step.setStatus(StepExecutionStatus.CANCELLED)
                .setFinishedAt(now)
                .setUpdatedAt(now)
                .setNextRetryAt(null)
                .setFailureReason(null)
                .setK8sJobName(null)
                .setStartedAt(null);
        stepExecutionRepository.save(step);
    }

    private ExecutionEvent stepCancelledEvent(Long executionId, String stepName, Instant now) {
        return new ExecutionEvent()
                .setWorkflowExecutionId(executionId)
                .setEventType(ExecutionEventType.STEP_CANCELLED)
                .setPayload(stepPayloadJson(stepName))
                .setCreatedAt(now);
    }

    private String stepPayloadJson(String stepName) {
        try {
            return objectMapper.writeValueAsString(Map.of("stepName", stepName));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private CreateWorkflowRequest parseDefinition(String definitionJson) {
        try {
            return objectMapper.readValue(definitionJson, CreateWorkflowRequest.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workflow definition JSON", e);
        }
    }

    private String buildCreationPayload(Long workflowId, Long workflowVersionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", workflowId);
        payload.put("workflowVersionId", workflowVersionId);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize execution event payload", e);
        }
    }

    private ExecutionResponse toResponse(
            WorkflowExecution execution,
            List<StepExecution> steps,
            List<ExecutionEvent> events
    ) {
        List<StepExecutionResponse> stepResponses = steps.stream()
                .map(s -> new StepExecutionResponse(
                        s.getId(),
                        s.getStepIndex(),
                        s.getStepName(),
                        s.getStatus(),
                        s.getAttempt(),
                        s.getMaxRetries(),
                        s.getRetryCount(),
                        s.getTimeoutSeconds(),
                        s.getFailureReason(),
                        s.getCreatedAt(),
                        s.getUpdatedAt()
                ))
                .toList();

        List<ExecutionEventResponse> eventResponses = events.stream()
                .map(e -> new ExecutionEventResponse(
                        e.getId(),
                        e.getEventType(),
                        e.getPayload(),
                        e.getCreatedAt()
                ))
                .toList();

        return new ExecutionResponse(
                execution.getId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersionId(),
                execution.getStatus(),
                execution.getCreatedAt(),
                execution.getUpdatedAt(),
                execution.getFinishedAt(),
                execution.getPausedAt(),
                execution.isCancelRequested(),
                execution.getCancelledAt(),
                stepResponses,
                eventResponses
        );
    }
}
