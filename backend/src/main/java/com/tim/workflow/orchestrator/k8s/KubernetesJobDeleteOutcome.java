package com.tim.workflow.orchestrator.k8s;

public record KubernetesJobDeleteOutcome(KubernetesJobDeleteResult result, Integer httpStatus, String message) {

    public static KubernetesJobDeleteOutcome skippedNoName() {
        return new KubernetesJobDeleteOutcome(KubernetesJobDeleteResult.SKIPPED_NO_NAME, null, null);
    }

    public static KubernetesJobDeleteOutcome deleted() {
        return new KubernetesJobDeleteOutcome(KubernetesJobDeleteResult.DELETED, null, null);
    }

    public static KubernetesJobDeleteOutcome notFound(int httpStatus, String message) {
        return new KubernetesJobDeleteOutcome(KubernetesJobDeleteResult.NOT_FOUND, httpStatus, message);
    }

    public static KubernetesJobDeleteOutcome deleteFailed(int httpStatus, String message) {
        return new KubernetesJobDeleteOutcome(KubernetesJobDeleteResult.DELETE_FAILED, httpStatus, message);
    }
}
