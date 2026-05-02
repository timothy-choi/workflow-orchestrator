package com.tim.workflow.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator")
public class OrchestratorProperties {

    private final Execution execution = new Execution();

    private final Callback callback = new Callback();

    private final Kubernetes kubernetes = new Kubernetes();

    public Execution getExecution() {
        return execution;
    }

    public Callback getCallback() {
        return callback;
    }

    public Kubernetes getKubernetes() {
        return kubernetes;
    }

    public static class Execution {

        private ExecutionMode mode = ExecutionMode.LOCAL;

        public ExecutionMode getMode() {
            return mode;
        }

        public void setMode(ExecutionMode mode) {
            this.mode = mode;
        }
    }

    public static class Callback {

        /**
         * Base URL of this orchestrator reachable by Kubernetes Jobs (no trailing slash), e.g. {@code http://workflow-orchestrator:8082}.
         */
        private String baseUrl = "http://localhost:8082";

        /**
         * Shared secret for {@code X-Callback-Token} on internal callbacks.
         */
        private String token = "dev-token-change-me";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class Kubernetes {

        private String namespace = "default";

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }
}
