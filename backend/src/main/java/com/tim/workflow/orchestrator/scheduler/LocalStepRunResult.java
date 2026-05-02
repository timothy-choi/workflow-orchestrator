package com.tim.workflow.orchestrator.scheduler;

/**
 * Outcome of running a workflow step command in local execution mode.
 */
public final class LocalStepRunResult {

    private final boolean success;
    private final boolean timedOut;
    private final String failureReason;

    private LocalStepRunResult(boolean success, boolean timedOut, String failureReason) {
        this.success = success;
        this.timedOut = timedOut;
        this.failureReason = failureReason;
    }

    public static LocalStepRunResult ok() {
        return new LocalStepRunResult(true, false, null);
    }

    public static LocalStepRunResult failed(String failureReason) {
        return new LocalStepRunResult(false, false, failureReason);
    }

    public static LocalStepRunResult timedOut() {
        return new LocalStepRunResult(false, true, "Step timed out");
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
