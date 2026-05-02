package com.tim.workflow.orchestrator.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tim.workflow.orchestrator.config.OrchestratorProperties;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;

@ExtendWith(MockitoExtension.class)
class KubernetesJobDeleterTest {

    @Test
    void deleteJobBestEffort_invokesBatchDelete() throws Exception {
        BatchV1Api api = mock(BatchV1Api.class, RETURNS_DEEP_STUBS);
        OrchestratorProperties props = new OrchestratorProperties();
        props.getKubernetes().setNamespace("test-ns");

        when(api.deleteNamespacedJob(eq("my-job"), eq("test-ns")).propagationPolicy(eq("Background")).execute())
                .thenReturn(null);

        KubernetesJobDeleter deleter = new KubernetesJobDeleter(api, props);
        assertThat(deleter.deleteJobBestEffort("my-job").result()).isEqualTo(KubernetesJobDeleteResult.DELETED);

        verify(api).deleteNamespacedJob(eq("my-job"), eq("test-ns"));
    }

    @Test
    void deleteJobBestEffort_maps404ToNotFound() throws Exception {
        BatchV1Api api = mock(BatchV1Api.class, RETURNS_DEEP_STUBS);
        OrchestratorProperties props = new OrchestratorProperties();
        props.getKubernetes().setNamespace("test-ns");

        when(api.deleteNamespacedJob(anyString(), anyString()).propagationPolicy(anyString()).execute())
                .thenThrow(new ApiException(404, Collections.emptyMap(), "not found"));

        KubernetesJobDeleter deleter = new KubernetesJobDeleter(api, props);
        KubernetesJobDeleteOutcome out = deleter.deleteJobBestEffort("gone-job");
        assertThat(out.result()).isEqualTo(KubernetesJobDeleteResult.NOT_FOUND);
        assertThat(out.httpStatus()).isEqualTo(404);
    }

    @Test
    void deleteJobBestEffort_mapsOtherApiErrorsToDeleteFailed() throws Exception {
        BatchV1Api api = mock(BatchV1Api.class, RETURNS_DEEP_STUBS);
        OrchestratorProperties props = new OrchestratorProperties();
        props.getKubernetes().setNamespace("test-ns");

        when(api.deleteNamespacedJob(anyString(), anyString()).propagationPolicy(anyString()).execute())
                .thenThrow(new ApiException(403, Collections.emptyMap(), "forbidden"));

        KubernetesJobDeleter deleter = new KubernetesJobDeleter(api, props);
        KubernetesJobDeleteOutcome out = deleter.deleteJobBestEffort("job-a");
        assertThat(out.result()).isEqualTo(KubernetesJobDeleteResult.DELETE_FAILED);
        assertThat(out.httpStatus()).isEqualTo(403);
    }

    @Test
    void deleteJobBestEffort_blankNameSkipped() {
        BatchV1Api api = mock(BatchV1Api.class, RETURNS_DEEP_STUBS);
        OrchestratorProperties props = new OrchestratorProperties();
        props.getKubernetes().setNamespace("test-ns");

        KubernetesJobDeleter deleter = new KubernetesJobDeleter(api, props);
        assertThat(deleter.deleteJobBestEffort("  ").result()).isEqualTo(KubernetesJobDeleteResult.SKIPPED_NO_NAME);
    }
}
