package com.tim.workflow.orchestrator.scheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.domain.StepExecution;

/**
 * Runs workflow step {@code command} locally via {@code /bin/sh -c "&lt;command&gt;"} (no simulated success).
 */
@Component
public class LocalStepRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalStepRunner.class);

    private static final int OUTPUT_CAP = 8000;

    public LocalStepRunResult run(String command, int timeoutSeconds, StepExecution step) {
        if (command == null || command.isBlank()) {
            return LocalStepRunResult.failedPreStart("Step command is empty");
        }

        long waitSeconds = timeoutSeconds < 1 ? 1L : timeoutSeconds;

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.warn("Could not start shell for step {}: {}", step.getStepName(), e.getMessage());
            return LocalStepRunResult.failedPreStart("Failed to start command: " + e.getMessage());
        }

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

        Thread drainOut = drainThread("local-step-out-" + safeId(step), process.getInputStream(), outBuf);
        Thread drainErr = drainThread("local-step-err-" + safeId(step), process.getErrorStream(), errBuf);
        drainOut.start();
        drainErr.start();

        boolean finished;
        try {
            finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            joinQuietly(drainOut);
            joinQuietly(drainErr);
            return LocalStepRunResult.failedPreStart("Interrupted while waiting for step command");
        }

        if (!finished) {
            process.destroyForcibly();
            joinQuietly(drainOut);
            joinQuietly(drainErr);
            log.warn("Local step timed out stepName={} executionId={} timeoutSeconds={}",
                    step.getStepName(), step.getWorkflowExecutionId(), timeoutSeconds);
            String outSnap = truncate(bytesToString(outBuf));
            String errSnap = truncate(bytesToString(errBuf));
            return LocalStepRunResult.timedOut(outSnap, errSnap);
        }

        joinQuietly(drainOut);
        joinQuietly(drainErr);

        int exit;
        try {
            exit = process.exitValue();
        } catch (IllegalThreadStateException e) {
            return LocalStepRunResult.failedPreStart("Process exit code not available");
        }

        String out = truncate(bytesToString(outBuf));
        String err = truncate(bytesToString(errBuf));

        if (exit != 0) {
            String reason = buildFailureReason(exit, out, err);
            log.debug("Local step failed stepName={} exit={}", step.getStepName(), exit);
            return LocalStepRunResult.failedExit(exit, out, err, reason);
        }

        log.debug("Local step succeeded stepName={} executionId={}", step.getStepName(), step.getWorkflowExecutionId());
        return LocalStepRunResult.succeeded(out, err);
    }

    private static String safeId(StepExecution step) {
        Long id = step.getId();
        return id != null ? String.valueOf(id) : "na";
    }

    private static Thread drainThread(String name, InputStream in, ByteArrayOutputStream target) {
        Thread t = new Thread(() -> {
            try {
                in.transferTo(target);
            } catch (IOException ignored) {
                // Stream closed after destroy or normal exit
            }
        }, name);
        t.setDaemon(true);
        return t;
    }

    private static String bytesToString(ByteArrayOutputStream buf) {
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(30_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.stripTrailing();
        if (t.length() <= OUTPUT_CAP) {
            return t;
        }
        return t.substring(0, OUTPUT_CAP) + "…(truncated)";
    }

    private static String buildFailureReason(int exitCode, String stdout, String stderr) {
        StringBuilder sb = new StringBuilder("Command exited with code ").append(exitCode);
        if (!stdout.isEmpty()) {
            sb.append("; stdout: ").append(stdout);
        }
        if (!stderr.isEmpty()) {
            sb.append("; stderr: ").append(stderr);
        }
        return sb.toString();
    }
}
