package com.tim.workflow.orchestrator.k8s;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.config.OrchestratorProperties;
import com.tim.workflow.orchestrator.domain.ExecutionEvent;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.logging.WorkflowLogContext;
import com.tim.workflow.orchestrator.metrics.WorkflowMetrics;
import com.tim.workflow.orchestrator.repository.ExecutionEventRepository;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;

@Component
@ConditionalOnProperty(prefix = "orchestrator.execution", name = "mode", havingValue = "kubernetes")
public class KubernetesJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KubernetesJobDispatcher.class);

    private final BatchV1Api batchV1Api;
    private final OrchestratorProperties orchestratorProperties;
    private final KubernetesJobNameFactory jobNameFactory;
    private final StepExecutionRepository stepExecutionRepository;
    private final ExecutionEventRepository executionEventRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowMetrics workflowMetrics;

    public KubernetesJobDispatcher(
            BatchV1Api batchV1Api,
            OrchestratorProperties orchestratorProperties,
            KubernetesJobNameFactory jobNameFactory,
            StepExecutionRepository stepExecutionRepository,
            ExecutionEventRepository executionEventRepository,
            ObjectMapper objectMapper,
            WorkflowMetrics workflowMetrics
    ) {
        this.batchV1Api = batchV1Api;
        this.orchestratorProperties = orchestratorProperties;
        this.jobNameFactory = jobNameFactory;
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionEventRepository = executionEventRepository;
        this.objectMapper = objectMapper;
        this.workflowMetrics = workflowMetrics;
    }

    /**
     * Creates a Kubernetes Job for the step; step remains {@link com.tim.workflow.orchestrator.domain.StepExecutionStatus#RUNNING}
     * until the worker POSTs to the callback URL.
     */
    public void dispatchJob(long workflowExecutionId, long workflowId, StepExecution step, WorkflowStepRequest stepDef)
            throws ApiException {
        String namespace = orchestratorProperties.getKubernetes().getNamespace();
        String jobName = jobNameFactory.jobName(workflowExecutionId, step.getId(), step.getAttempt());
        Instant now = Instant.now();

        if (jobName.equals(step.getK8sJobName())) {
            try {
                batchV1Api.readNamespacedJob(jobName, namespace).execute();
                saveJobEvent(
                        workflowExecutionId,
                        ExecutionEventType.JOB_ALREADY_EXISTS,
                        jobName,
                        now
                );
                log.info("Kubernetes Job {} already present; skipping create", jobName);
                return;
            } catch (ApiException e) {
                if (e.getCode() != 404) {
                    throw e;
                }
                saveJobEvent(workflowExecutionId, ExecutionEventType.JOB_MISSING, jobName, now);
                log.warn("Recorded JOB_MISSING for {}; recreating", jobName);
            }
        }

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "workflow-orchestrator");
        labels.put("executionId", String.valueOf(workflowExecutionId));
        labels.put("stepExecutionId", String.valueOf(step.getId()));
        labels.put("attempt", String.valueOf(step.getAttempt()));

        String callbackUrl = callbackUrl();

        List<V1EnvVar> env = new ArrayList<>();
        env.add(new V1EnvVar().name("EXECUTION_ID").value(String.valueOf(workflowExecutionId)));
        env.add(new V1EnvVar().name("STEP_EXECUTION_ID").value(String.valueOf(step.getId())));
        env.add(new V1EnvVar().name("CALLBACK_URL").value(callbackUrl));
        env.add(new V1EnvVar().name("CALLBACK_TOKEN").value(orchestratorProperties.getCallback().getToken()));
        env.add(new V1EnvVar().name("STEP_COMMAND").value(stepDef.getCommand()));
        env.add(new V1EnvVar().name("STEP_TIMEOUT_SECONDS").value(String.valueOf(step.getTimeoutSeconds())));

        String workerImage = orchestratorProperties.getKubernetes().getWorkerImage();

        V1Container container = new V1Container()
                .name("step")
                .image(workerImage)
                .env(env);

        V1PodSpec podSpec = new V1PodSpec()
                .restartPolicy("Never")
                .containers(List.of(container));

        V1Job job = new V1Job()
                .metadata(new V1ObjectMeta().name(jobName).labels(labels))
                .spec(new V1JobSpec()
                        .backoffLimit(0)
                        .template(new V1PodTemplateSpec()
                                .spec(podSpec)));

        try {
            batchV1Api.createNamespacedJob(namespace, job).execute();
        } catch (ApiException e) {
            workflowMetrics.recordKubernetesJobCreateFailure();
            throw e;
        }

        StepExecution managed = stepExecutionRepository.findById(step.getId()).orElseThrow();
        managed.setK8sJobName(jobName).setUpdatedAt(now);
        stepExecutionRepository.save(managed);

        saveJobEvent(workflowExecutionId, ExecutionEventType.JOB_CREATED, jobName, now);
        WorkflowLogContext.put(workflowExecutionId, step.getId(), workflowId, jobName, "JOB_CREATED");
        try {
            log.info("Kubernetes Job created namespace={} jobName={}", namespace, jobName);
        } finally {
            WorkflowLogContext.clear();
        }
    }

    private void saveJobEvent(Long executionId, ExecutionEventType type, String jobName, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobName", jobName);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        executionEventRepository.save(new ExecutionEvent()
                .setWorkflowExecutionId(executionId)
                .setEventType(type)
                .setPayload(json)
                .setCreatedAt(now));
    }

    private String callbackUrl() {
        String base = orchestratorProperties.getCallback().getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/internal/step-results";
    }
}
