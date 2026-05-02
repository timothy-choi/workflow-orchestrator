package com.tim.workflow.orchestrator.service;

public enum StepCallbackOutcome {
    /** Callback applied or idempotent no-op for duplicate success. */
    ACCEPTED,
    /** Execution or step was cancelled; response should be 200 OK so workers stop retrying. */
    IGNORED_CANCELLED
}
