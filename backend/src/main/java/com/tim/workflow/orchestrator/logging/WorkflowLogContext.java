package com.tim.workflow.orchestrator.logging;

import org.slf4j.MDC;

/**
 * Puts workflow correlation fields on the logging {@link MDC} for the console pattern in {@code application.yaml}.
 */
public final class WorkflowLogContext {

    public static final String EXECUTION_ID = "executionId";
    public static final String STEP_EXECUTION_ID = "stepExecutionId";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String K8S_JOB_NAME = "k8sJobName";
    public static final String EVENT_TYPE = "eventType";

    private WorkflowLogContext() {
    }

    public static void put(Long executionId, Long stepExecutionId, Long workflowId, String k8sJobName, String eventType) {
        if (executionId != null) {
            MDC.put(EXECUTION_ID, String.valueOf(executionId));
        }
        if (stepExecutionId != null) {
            MDC.put(STEP_EXECUTION_ID, String.valueOf(stepExecutionId));
        }
        if (workflowId != null) {
            MDC.put(WORKFLOW_ID, String.valueOf(workflowId));
        }
        if (k8sJobName != null && !k8sJobName.isBlank()) {
            MDC.put(K8S_JOB_NAME, k8sJobName);
        }
        if (eventType != null && !eventType.isBlank()) {
            MDC.put(EVENT_TYPE, eventType);
        }
    }

    public static void clear() {
        MDC.remove(EXECUTION_ID);
        MDC.remove(STEP_EXECUTION_ID);
        MDC.remove(WORKFLOW_ID);
        MDC.remove(K8S_JOB_NAME);
        MDC.remove(EVENT_TYPE);
    }

    public static Runnable wrap(
            Long executionId,
            Long stepExecutionId,
            Long workflowId,
            String k8sJobName,
            String eventType,
            Runnable runnable
    ) {
        return () -> {
            put(executionId, stepExecutionId, workflowId, k8sJobName, eventType);
            try {
                runnable.run();
            } finally {
                clear();
            }
        };
    }
}
