package com.tim.workflow.orchestrator.domain;

public enum StepExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRY_WAIT,
    CANCELLED
}
