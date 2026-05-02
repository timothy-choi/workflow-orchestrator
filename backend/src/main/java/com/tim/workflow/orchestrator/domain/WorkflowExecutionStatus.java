package com.tim.workflow.orchestrator.domain;

public enum WorkflowExecutionStatus {
    CREATED,
    RUNNING,
    PAUSED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
