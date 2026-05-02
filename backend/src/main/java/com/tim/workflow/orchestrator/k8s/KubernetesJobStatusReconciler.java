package com.tim.workflow.orchestrator.k8s;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tim.workflow.orchestrator.config.OrchestratorProperties;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;
import com.tim.workflow.orchestrator.service.StepCallbackService;
import com.tim.workflow.orchestrator.service.StepRetryCoordinator;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobStatus;

@Component
@ConditionalOnProperty(prefix = "orchestrator.execution", name = "mode", havingValue = "kubernetes")
public class KubernetesJobStatusReconciler {

    private static final Logger log = LoggerFactory.getLogger(KubernetesJobStatusReconciler.class);

    private final BatchV1Api batchV1Api;
    private final OrchestratorProperties orchestratorProperties;
    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final StepCallbackService stepCallbackService;
    private final StepRetryCoordinator stepRetryCoordinator;
    private final KubernetesJobStatusReconciler self;

    public KubernetesJobStatusReconciler(
            BatchV1Api batchV1Api,
            OrchestratorProperties orchestratorProperties,
            StepExecutionRepository stepExecutionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            StepCallbackService stepCallbackService,
            StepRetryCoordinator stepRetryCoordinator,
            @Lazy KubernetesJobStatusReconciler self
    ) {
        this.batchV1Api = batchV1Api;
        this.orchestratorProperties = orchestratorProperties;
        this.stepExecutionRepository = stepExecutionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.stepCallbackService = stepCallbackService;
        this.stepRetryCoordinator = stepRetryCoordinator;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${workflow.scheduler.reconcile-delay-ms:10000}")
    public void tick() {
        List<StepExecution> candidates = stepExecutionRepository.findRunningWithK8sJobForActiveExecutions(
                StepExecutionStatus.RUNNING,
                EnumSet.of(
                        WorkflowExecutionStatus.CREATED,
                        WorkflowExecutionStatus.RUNNING,
                        WorkflowExecutionStatus.PAUSED)
        );
        for (StepExecution step : candidates) {
            try {
                self.reconcileStep(step.getId());
            } catch (Exception e) {
                log.warn("Failed to reconcile step {}", step.getId(), e);
            }
        }
    }

    @Transactional
    public void reconcileStep(Long stepId) {
        StepExecution step = stepExecutionRepository.findById(stepId).orElse(null);
        if (step == null || step.getStatus() != StepExecutionStatus.RUNNING || step.getK8sJobName() == null) {
            return;
        }

        WorkflowExecution execution = workflowExecutionRepository.findById(step.getWorkflowExecutionId()).orElse(null);
        if (execution == null
                || execution.getStatus() == WorkflowExecutionStatus.CANCELLED
                || execution.isCancelRequested()) {
            return;
        }

        String ns = orchestratorProperties.getKubernetes().getNamespace();
        String jobName = step.getK8sJobName();
        Instant now = Instant.now();

        try {
            V1Job job = batchV1Api.readNamespacedJob(jobName, ns).execute();
            V1JobStatus status = job.getStatus();
            if (status == null) {
                return;
            }
            Integer succeeded = status.getSucceeded();
            if (succeeded != null && succeeded > 0) {
                stepCallbackService.applyReconcilerJobSucceeded(
                        step.getWorkflowExecutionId(),
                        step.getId(),
                        "Kubernetes Job succeeded (callback missing)",
                        now
                );
                return;
            }
            Integer failed = status.getFailed();
            if (failed != null && failed > 0) {
                applyJobFailure(step.getId(), "Kubernetes Job failed (callback missing)",
                        ExecutionEventType.CALLBACK_MISSING_JOB_FAILED, now);
            }
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                applyJobFailure(step.getId(), "Job not found in cluster",
                        ExecutionEventType.JOB_NOT_FOUND, now);
            } else {
                log.warn("Api error reading Job {}: {}", jobName, e.getMessage());
            }
        }
    }

    private void applyJobFailure(Long stepId, String reason, ExecutionEventType diagnostic, Instant now) {
        StepExecution fresh = stepExecutionRepository.findById(stepId).orElse(null);
        if (fresh == null || fresh.getStatus() != StepExecutionStatus.RUNNING) {
            return;
        }
        WorkflowExecution execution = workflowExecutionRepository.findById(fresh.getWorkflowExecutionId())
                .orElse(null);
        if (execution == null) {
            return;
        }
        stepRetryCoordinator.handleFailureWithDiagnostic(execution, fresh, reason, diagnostic, now);
    }
}
