package com.tim.workflow.orchestrator.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public ExecutionService(
            WorkflowRepository workflowRepository,
            WorkflowVersionRepository workflowVersionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            StepExecutionRepository stepExecutionRepository,
            ExecutionEventRepository executionEventRepository,
            ObjectMapper objectMapper
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionEventRepository = executionEventRepository;
        this.objectMapper = objectMapper;
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
                stepResponses,
                eventResponses
        );
    }
}
