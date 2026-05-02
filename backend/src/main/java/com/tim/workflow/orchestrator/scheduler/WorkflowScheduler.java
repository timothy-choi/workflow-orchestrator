package com.tim.workflow.orchestrator.scheduler;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.config.ExecutionMode;
import com.tim.workflow.orchestrator.config.OrchestratorProperties;
import com.tim.workflow.orchestrator.domain.ExecutionEvent;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowVersion;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.k8s.KubernetesJobDispatcher;
import com.tim.workflow.orchestrator.repository.ExecutionEventRepository;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowVersionRepository;
import com.tim.workflow.orchestrator.service.StepRetryCoordinator;

import io.kubernetes.client.openapi.ApiException;

@Component
public class WorkflowScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ExecutionEventRepository executionEventRepository;
    private final DependencyResolver dependencyResolver;
    private final LocalStepRunner localStepRunner;
    private final OrchestratorProperties orchestratorProperties;
    private final ObjectProvider<KubernetesJobDispatcher> kubernetesJobDispatcher;
    private final StepRetryCoordinator stepRetryCoordinator;
    private final ObjectMapper objectMapper;
    private final WorkflowScheduler self;

    private final boolean tickEnabled;

    public WorkflowScheduler(
            WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowVersionRepository workflowVersionRepository,
            StepExecutionRepository stepExecutionRepository,
            ExecutionEventRepository executionEventRepository,
            DependencyResolver dependencyResolver,
            LocalStepRunner localStepRunner,
            OrchestratorProperties orchestratorProperties,
            ObjectProvider<KubernetesJobDispatcher> kubernetesJobDispatcher,
            StepRetryCoordinator stepRetryCoordinator,
            ObjectMapper objectMapper,
            @Lazy WorkflowScheduler self,
            @Value("${workflow.scheduler.tick-enabled:true}") boolean tickEnabled
    ) {
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionEventRepository = executionEventRepository;
        this.dependencyResolver = dependencyResolver;
        this.localStepRunner = localStepRunner;
        this.orchestratorProperties = orchestratorProperties;
        this.kubernetesJobDispatcher = kubernetesJobDispatcher;
        this.stepRetryCoordinator = stepRetryCoordinator;
        this.objectMapper = objectMapper;
        this.self = self;
        this.tickEnabled = tickEnabled;
    }

    /**
     * Poll interval between ticks (2–5s range requested; default 3s).
     */
    @Scheduled(
            fixedDelayString = "${workflow.scheduler.fixed-delay-ms:3000}",
            initialDelayString = "${workflow.scheduler.initial-delay-ms:1500}"
    )
    public void tick() {
        if (!tickEnabled) {
            return;
        }
        List<WorkflowExecution> active = workflowExecutionRepository.findByStatusIn(
                EnumSet.of(WorkflowExecutionStatus.CREATED, WorkflowExecutionStatus.RUNNING));
        for (WorkflowExecution execution : active) {
            try {
                self.processExecution(execution.getId());
            } catch (Exception e) {
                log.warn("Scheduler failed for execution {}", execution.getId(), e);
            }
        }
    }

    /**
     * Runs one scheduling pass for a single execution under a pessimistic lock (avoids duplicate dispatch).
     */
    @Transactional
    public void processExecution(Long executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findLockedById(executionId)
                .orElseThrow(() -> new IllegalStateException("Workflow execution not found: " + executionId));

        if (execution.getStatus() != WorkflowExecutionStatus.CREATED
                && execution.getStatus() != WorkflowExecutionStatus.RUNNING) {
            return;
        }

        Instant now = Instant.now();
        if (execution.getStatus() == WorkflowExecutionStatus.CREATED) {
            execution.setStatus(WorkflowExecutionStatus.RUNNING).setUpdatedAt(now);
            workflowExecutionRepository.save(execution);
        }

        WorkflowVersion version = workflowVersionRepository.findById(execution.getWorkflowVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Workflow version not found: " + execution.getWorkflowVersionId()));

        CreateWorkflowRequest definition = parseDefinition(version.getDefinitionJson());

        promoteRetryWaitSteps(executionId, now);
        failTimedOutRunningSteps(execution, executionId, now);

        if (execution.getStatus() == WorkflowExecutionStatus.FAILED) {
            return;
        }

        while (true) {
            if (execution.getStatus() == WorkflowExecutionStatus.FAILED) {
                break;
            }

            List<StepExecution> stepRows =
                    stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
            Map<String, StepExecution> stepsByName = new LinkedHashMap<>();
            for (StepExecution s : stepRows) {
                stepsByName.put(s.getStepName(), s);
            }

            List<WorkflowStepRequest> runnableDefs = dependencyResolver.resolveRunnable(definition, stepsByName);
            if (runnableDefs.isEmpty()) {
                break;
            }

            boolean progressed = false;
            for (WorkflowStepRequest stepDef : runnableDefs) {
                StepExecution snapshot = stepsByName.get(stepDef.getName());
                if (snapshot == null) {
                    continue;
                }
                if (snapshot.getStatus() != StepExecutionStatus.PENDING) {
                    continue;
                }

                int claimed = stepExecutionRepository.claimPendingToRunning(
                        snapshot.getId(),
                        Instant.now(),
                        StepExecutionStatus.PENDING,
                        StepExecutionStatus.RUNNING
                );
                if (claimed == 0) {
                    continue;
                }

                progressed = true;
                StepExecution running = stepExecutionRepository.findById(snapshot.getId()).orElseThrow();
                Instant runStart = running.getStartedAt() != null ? running.getStartedAt() : Instant.now();

                executionEventRepository.save(new ExecutionEvent()
                        .setWorkflowExecutionId(executionId)
                        .setEventType(ExecutionEventType.STEP_STARTED)
                        .setPayload(stepPayload(running.getStepName()))
                        .setCreatedAt(runStart));

                if (orchestratorProperties.getExecution().getMode() == ExecutionMode.LOCAL) {
                    localStepRunner.simulateSuccess(running);

                    Instant runEnd = Instant.now();
                    running.setStatus(StepExecutionStatus.SUCCESS)
                            .setFinishedAt(runEnd)
                            .setUpdatedAt(runEnd);
                    stepExecutionRepository.save(running);

                    executionEventRepository.save(new ExecutionEvent()
                            .setWorkflowExecutionId(executionId)
                            .setEventType(ExecutionEventType.STEP_SUCCEEDED)
                            .setPayload(stepPayload(running.getStepName()))
                            .setCreatedAt(runEnd));
                } else {
                    KubernetesJobDispatcher dispatcher = kubernetesJobDispatcher.getIfAvailable();
                    if (dispatcher == null) {
                        throw new IllegalStateException(
                                "KubernetesJobDispatcher bean missing while orchestrator.execution.mode=kubernetes");
                    }
                    try {
                        dispatcher.dispatchJob(executionId, running, stepDef);
                    } catch (ApiException e) {
                        throw new IllegalStateException("Failed to create Kubernetes Job: " + e.getMessage(), e);
                    }
                }
            }

            if (!progressed) {
                break;
            }
        }

        List<StepExecution> refreshed =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        boolean allSuccess = !refreshed.isEmpty()
                && refreshed.stream().allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);

        if (allSuccess && execution.getStatus() == WorkflowExecutionStatus.RUNNING) {
            Instant finished = Instant.now();
            execution.setStatus(WorkflowExecutionStatus.SUCCEEDED)
                    .setUpdatedAt(finished)
                    .setFinishedAt(finished);
            workflowExecutionRepository.save(execution);

            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(executionId)
                    .setEventType(ExecutionEventType.EXECUTION_SUCCEEDED)
                    .setPayload("{}")
                    .setCreatedAt(finished));
        }
    }

    private void promoteRetryWaitSteps(Long executionId, Instant now) {
        List<StepExecution> steps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        for (StepExecution s : steps) {
            if (s.getStatus() != StepExecutionStatus.RETRY_WAIT || s.getNextRetryAt() == null) {
                continue;
            }
            if (s.getNextRetryAt().isAfter(now)) {
                continue;
            }
            s.setStatus(StepExecutionStatus.PENDING)
                    .setNextRetryAt(null)
                    .setUpdatedAt(now);
            stepExecutionRepository.save(s);

            executionEventRepository.save(new ExecutionEvent()
                    .setWorkflowExecutionId(executionId)
                    .setEventType(ExecutionEventType.STEP_RETRY_READY)
                    .setPayload(stepPayload(s.getStepName()))
                    .setCreatedAt(now));
        }
    }

    private void failTimedOutRunningSteps(WorkflowExecution execution, Long executionId, Instant now) {
        if (execution.getStatus() == WorkflowExecutionStatus.FAILED) {
            return;
        }
        List<StepExecution> steps =
                stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        for (StepExecution s : steps) {
            if (execution.getStatus() == WorkflowExecutionStatus.FAILED) {
                break;
            }
            if (s.getStatus() != StepExecutionStatus.RUNNING || s.getStartedAt() == null) {
                continue;
            }
            Instant deadline = s.getStartedAt().plusSeconds(s.getTimeoutSeconds());
            if (!deadline.isBefore(now)) {
                continue;
            }
            stepRetryCoordinator.handleFailureWithDiagnostic(
                    execution,
                    s,
                    "Step timed out after " + s.getTimeoutSeconds() + "s",
                    ExecutionEventType.STEP_TIMED_OUT,
                    now
            );
        }
    }

    private CreateWorkflowRequest parseDefinition(String definitionJson) {
        try {
            return objectMapper.readValue(definitionJson, CreateWorkflowRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid workflow definition JSON for execution", e);
        }
    }

    private String stepPayload(String stepName) {
        try {
            return objectMapper.writeValueAsString(Map.of("stepName", stepName));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize step event payload", e);
        }
    }
}
