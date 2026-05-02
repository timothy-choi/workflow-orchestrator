package com.tim.workflow.orchestrator.k8s;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Builds RFC 1123 DNS subdomain Job names (≤63 chars) from execution and step ids.
 */
@Component
public class KubernetesJobNameFactory {

    /**
     * Compact deterministic name: {@code wf-e{hex}-s{hex}} (fits within 63 chars for all long values).
     */
    public String jobName(long workflowExecutionId, long stepExecutionId) {
        String name = String.format(Locale.ROOT, "wf-e%x-s%x", workflowExecutionId, stepExecutionId);
        return sanitize(name);
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
