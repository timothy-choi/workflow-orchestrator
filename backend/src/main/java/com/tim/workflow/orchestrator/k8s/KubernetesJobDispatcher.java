package com.tim.workflow.orchestrator.k8s;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.config.OrchestratorProperties;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;

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

    public KubernetesJobDispatcher(
            BatchV1Api batchV1Api,
            OrchestratorProperties orchestratorProperties,
            KubernetesJobNameFactory jobNameFactory
    ) {
        this.batchV1Api = batchV1Api;
        this.orchestratorProperties = orchestratorProperties;
        this.jobNameFactory = jobNameFactory;
    }

    /**
     * Creates a Kubernetes Job for the step; step remains {@link com.tim.workflow.orchestrator.domain.StepExecutionStatus#RUNNING}
     * until the worker POSTs to the callback URL.
     */
    public void dispatchJob(long workflowExecutionId, StepExecution step, WorkflowStepRequest stepDef)
            throws ApiException {
        String namespace = orchestratorProperties.getKubernetes().getNamespace();
        String jobName = jobNameFactory.jobName(workflowExecutionId, step.getId());

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "workflow-orchestrator");
        labels.put("executionId", String.valueOf(workflowExecutionId));
        labels.put("stepExecutionId", String.valueOf(step.getId()));

        String callbackUrl = callbackUrl();

        List<V1EnvVar> env = new ArrayList<>();
        env.add(new V1EnvVar().name("EXECUTION_ID").value(String.valueOf(workflowExecutionId)));
        env.add(new V1EnvVar().name("STEP_EXECUTION_ID").value(String.valueOf(step.getId())));
        env.add(new V1EnvVar().name("CALLBACK_URL").value(callbackUrl));
        env.add(new V1EnvVar().name("CALLBACK_TOKEN").value(orchestratorProperties.getCallback().getToken()));

        List<String> containerCommand = List.of("/bin/sh", "-c", stepDef.getCommand());

        V1Container container = new V1Container()
                .name("step")
                .image(stepDef.getImage())
                .command(containerCommand)
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

        batchV1Api.createNamespacedJob(namespace, job).execute();
        log.info("Created Kubernetes Job {} in namespace {}", jobName, namespace);
    }

    private String callbackUrl() {
        String base = orchestratorProperties.getCallback().getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/internal/step-results";
    }
}
