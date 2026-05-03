package com.tim.workflow.orchestrator.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.config.OrchestratorProperties;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;

@Component
@ConditionalOnProperty(prefix = "orchestrator.execution", name = "mode", havingValue = "kubernetes")
public class KubernetesJobDeleter {

    private static final Logger log = LoggerFactory.getLogger(KubernetesJobDeleter.class);

    private final BatchV1Api batchV1Api;
    private final OrchestratorProperties orchestratorProperties;

    public KubernetesJobDeleter(BatchV1Api batchV1Api, OrchestratorProperties orchestratorProperties) {
        this.batchV1Api = batchV1Api;
        this.orchestratorProperties = orchestratorProperties;
    }

    /**
     * Deletes a Job if it exists. Never throws; callers decide how to record outcomes.
     */
    public KubernetesJobDeleteOutcome deleteJobBestEffort(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return KubernetesJobDeleteOutcome.skippedNoName();
        }
        String ns = orchestratorProperties.getKubernetes().getNamespace();
        try {
            batchV1Api.deleteNamespacedJob(jobName, ns)
                    .propagationPolicy("Background")
                    .execute();
            log.info("Deleted Kubernetes Job {} in namespace {}", jobName, ns);
            return KubernetesJobDeleteOutcome.deleted();
        } catch (ApiException e) {
            if (isNotFound(e)) {
                log.debug("Kubernetes Job {} not found for delete", jobName);
                return KubernetesJobDeleteOutcome.notFound(e.getCode(), e.getMessage());
            }
            log.warn("Failed to delete Kubernetes Job {}: {}", jobName, e.getMessage());
            return KubernetesJobDeleteOutcome.deleteFailed(e.getCode(), e.getMessage());
        }
    }

    private static boolean isNotFound(ApiException e) {
        if (e.getCode() == 404) {
            return true;
        }
        String body = e.getResponseBody();
        return body != null && body.toLowerCase().contains("notfound");
    }
}
