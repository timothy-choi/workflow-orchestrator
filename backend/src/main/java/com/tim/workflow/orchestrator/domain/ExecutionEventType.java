package com.tim.workflow.orchestrator.domain;

public enum ExecutionEventType {
    EXECUTION_CREATED,
    STEP_STARTED,
    STEP_SUCCEEDED,
    STEP_FAILED,
    EXECUTION_SUCCEEDED
}
