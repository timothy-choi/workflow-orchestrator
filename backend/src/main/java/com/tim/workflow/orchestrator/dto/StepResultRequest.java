package com.tim.workflow.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class StepResultRequest {

    @NotNull
    private Long executionId;

    @NotNull
    private Long stepExecutionId;

    @NotNull
    private StepResultStatus status;

    @NotBlank
    private String message;

    private String logsRef;

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getStepExecutionId() {
        return stepExecutionId;
    }

    public void setStepExecutionId(Long stepExecutionId) {
        this.stepExecutionId = stepExecutionId;
    }

    public StepResultStatus getStatus() {
        return status;
    }

    public void setStatus(StepResultStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLogsRef() {
        return logsRef;
    }

    public void setLogsRef(String logsRef) {
        this.logsRef = logsRef;
    }

    public enum StepResultStatus {
        SUCCESS,
        FAILED
    }
}
