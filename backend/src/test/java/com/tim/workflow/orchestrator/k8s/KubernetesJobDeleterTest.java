package com.tim.workflow.orchestrator.k8s;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tim.workflow.orchestrator.config.OrchestratorProperties;

import io.kubernetes.client.openapi.apis.BatchV1Api;

@ExtendWith(MockitoExtension.class)
class KubernetesJobDeleterTest {

    @Test
    void deleteJobIfPresent_invokesBatchDelete() throws Exception {
        BatchV1Api api = mock(BatchV1Api.class, RETURNS_DEEP_STUBS);
        OrchestratorProperties props = new OrchestratorProperties();
        props.getKubernetes().setNamespace("test-ns");

        KubernetesJobDeleter deleter = new KubernetesJobDeleter(api, props);
        deleter.deleteJobIfPresent("my-job");

        verify(api).deleteNamespacedJob(eq("my-job"), eq("test-ns"));
    }
}
