package com.tim.workflow.orchestrator.scheduler;

/**
 * Outcome of running a workflow step command in local execution mode (real {@code /bin/sh -c} process).
 */
public final class LocalStepRunResult {

    private final boolean success;
    private final boolean timedOut;
    private final Integer exitCode;
    private final String stdout;
    private final String stderr;
    private final String failureReason;

    private LocalStepRunResult(
            boolean success,
            boolean timedOut,
            Integer exitCode,
            String stdout,
            String stderr,
            String failureReason
    ) {
        this.success = success;
        this.timedOut = timedOut;
        this.exitCode = exitCode;
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
        this.failureReason = failureReason;
    }

    /**
     * Command exited 0 and completed within the timeout (not interrupted).
     */
    public boolean isSuccess() {
        return success && !timedOut && exitCode != null && exitCode == 0;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    /**
     * Human-readable failure reason when {@link #isSuccess()} is false; may be null when succeeded.
     */
    public String getFailureReason() {
        return failureReason;
    }

    public static LocalStepRunResult succeeded(String stdout, String stderr) {
        return new LocalStepRunResult(true, false, 0, stdout, stderr, null);
    }

    public static LocalStepRunResult timedOut(String stdout, String stderr) {
        return new LocalStepRunResult(false, true, null, stdout, stderr, "Step timed out");
    }

    public static LocalStepRunResult failedExit(int exitCode, String stdout, String stderr, String failureReason) {
        return new LocalStepRunResult(false, false, exitCode, stdout, stderr, failureReason);
    }

    public static LocalStepRunResult failedPreStart(String failureReason) {
        return new LocalStepRunResult(false, false, null, "", "", failureReason);
    }
}
