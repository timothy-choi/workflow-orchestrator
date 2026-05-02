package com.tim.workflow.orchestrator.k8s;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Builds Kubernetes Job names (≤63 chars) including execution id, step execution id, and attempt.
 */
@Component
public class KubernetesJobNameFactory {

    /**
     * Preferred name: {@code workflow-exec-{e}-step-{s}-attempt-{a}}. Falls back to a compact form if too long.
     */
    public String jobName(long workflowExecutionId, long stepExecutionId, int attempt) {
        String primary = String.format(
                Locale.ROOT,
                "workflow-exec-%d-step-%d-attempt-%d",
                workflowExecutionId,
                stepExecutionId,
                attempt
        );
        if (primary.length() <= 63) {
            return sanitize(primary);
        }
        String compact = String.format(
                Locale.ROOT,
                "wf-exec%x-st%x-a%d",
                workflowExecutionId,
                stepExecutionId,
                attempt
        );
        return sanitize(compact);
    }

    String sanitize(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]", "-");
        lower = lower.replaceAll("-{2,}", "-");
        lower = lower.replaceAll("(^-+)|(-+$)", "");
        if (lower.length() <= 63) {
            return lower;
        }
        return lower.substring(0, 63).replaceAll("-+$", "");
    }
}
