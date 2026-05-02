package com.tim.workflow.orchestrator.k8s;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.util.ClientBuilder;

@Configuration
@ConditionalOnProperty(prefix = "orchestrator.execution", name = "mode", havingValue = "kubernetes")
public class KubernetesClientConfig {

    @Bean
    public ApiClient kubernetesApiClient() throws IOException {
        ApiClient client = ClientBuilder.standard().build();
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }

    @Bean
    public BatchV1Api kubernetesBatchV1Api(ApiClient kubernetesApiClient) {
        return new BatchV1Api(kubernetesApiClient);
    }
}
