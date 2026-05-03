package com.tim.workflow.orchestrator.k8s;

public enum KubernetesJobDeleteResult {
    SKIPPED_NO_NAME,
    DELETED,
    NOT_FOUND,
    DELETE_FAILED
}
