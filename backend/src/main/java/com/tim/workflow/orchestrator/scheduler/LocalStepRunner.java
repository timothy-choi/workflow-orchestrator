package com.tim.workflow.orchestrator.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.domain.StepExecution;

/**
 * Simulates step execution locally for scheduler/state-machine testing.
 *
 * <p>TODO: Replace this simulator with Kubernetes Job dispatch and worker integration.
 * TODO: Remove {@link Thread#sleep(long)}-based simulation once real execution is wired.
 */
@Component
public class LocalStepRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalStepRunner.class);

    private final int simulateStepDelayMs;

    public LocalStepRunner(
            @Value("${workflow.scheduler.simulate-step-delay-ms:50}") int simulateStepDelayMs
    ) {
        this.simulateStepDelayMs = simulateStepDelayMs;
    }

    /**
     * Blocks briefly (if configured) then returns — the step is treated as successful by the caller.
     */
    public void simulateSuccess(StepExecution step) {
        if (simulateStepDelayMs > 0) {
            try {
                Thread.sleep(simulateStepDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during simulated step run", e);
            }
        }
        log.debug("Simulated SUCCESS for step {} (execution {})", step.getStepName(), step.getWorkflowExecutionId());
    }
}
