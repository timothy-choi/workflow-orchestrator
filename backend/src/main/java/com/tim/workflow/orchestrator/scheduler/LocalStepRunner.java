package com.tim.workflow.orchestrator.scheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.domain.StepExecution;

/**
 * Runs workflow step {@code command} locally via {@code /bin/sh -c}.
 */
@Component
public class LocalStepRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalStepRunner.class);

    private static final int OUTPUT_CAP = 8000;

    private final int simulateStepDelayMs;

    public LocalStepRunner(
            @Value("${workflow.scheduler.simulate-step-delay-ms:50}") int simulateStepDelayMs
    ) {
        this.simulateStepDelayMs = simulateStepDelayMs;
    }

    /**
     * Executes {@code command} with {@code timeoutSeconds} wall-clock budget (minimum 1 second when {@code timeoutSeconds &lt; 1}).
     */
    public LocalStepRunResult run(String command, int timeoutSeconds, StepExecution step) {
        if (simulateStepDelayMs > 0) {
            try {
                Thread.sleep(simulateStepDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return LocalStepRunResult.failed("Interrupted before running step command");
            }
        }

        long waitSeconds = timeoutSeconds < 1 ? 1L : timeoutSeconds;

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.warn("Could not start shell for step {}: {}", step.getStepName(), e.getMessage());
            return LocalStepRunResult.failed("Failed to start command: " + e.getMessage());
        }

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread drainer = new Thread(() -> {
            try {
                process.getInputStream().transferTo(captured);
            } catch (IOException ignored) {
                // Process may have been destroyed
            }
        }, "local-step-drain-" + step.getId());
        drainer.setDaemon(true);
        drainer.start();

        boolean finished;
        try {
            finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            joinQuietly(drainer);
            return LocalStepRunResult.failed("Interrupted while waiting for step command");
        }

        if (!finished) {
            process.destroyForcibly();
            joinQuietly(drainer);
            log.warn("Local step timed out stepName={} executionId={} timeoutSeconds={}",
                    step.getStepName(), step.getWorkflowExecutionId(), timeoutSeconds);
            return LocalStepRunResult.timedOut();
        }

        joinQuietly(drainer);

        int exit = process.exitValue();
        String output = truncateOutput(captured.toString(StandardCharsets.UTF_8));

        if (exit != 0) {
            String reason = buildFailureReason(exit, output);
            log.debug("Local step failed stepName={} exit={} outputSnippet={}",
                    step.getStepName(), exit, abbreviateForLog(output));
            return LocalStepRunResult.failed(reason);
        }

        log.debug("Local step succeeded stepName={} executionId={}", step.getStepName(), step.getWorkflowExecutionId());
        return LocalStepRunResult.ok();
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(30_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncateOutput(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.stripTrailing();
        if (t.length() <= OUTPUT_CAP) {
            return t;
        }
        return t.substring(0, OUTPUT_CAP) + "…(truncated)";
    }

    private static String buildFailureReason(int exitCode, String output) {
        if (output.isEmpty()) {
            return "Command exited with code " + exitCode;
        }
        return "Command exited with code " + exitCode + ": " + output;
    }

    private static String abbreviateForLog(String output) {
        if (output.length() <= 200) {
            return output;
        }
        return output.substring(0, 200) + "…";
    }
}
