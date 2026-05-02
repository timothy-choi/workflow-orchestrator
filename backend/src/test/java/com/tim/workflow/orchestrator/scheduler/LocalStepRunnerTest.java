package com.tim.workflow.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tim.workflow.orchestrator.domain.StepExecution;

class LocalStepRunnerTest {

    private final LocalStepRunner runner = new LocalStepRunner(0);

    private static StepExecution dummyStep() {
        return new StepExecution()
                .setWorkflowExecutionId(99L)
                .setStepName("test-step");
    }

    @Test
    void echoHello_succeeds() {
        LocalStepRunResult r = runner.run("echo hello", 30, dummyStep());
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    void exitOne_fails() {
        LocalStepRunResult r = runner.run("exit 1", 30, dummyStep());
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.isTimedOut()).isFalse();
        assertThat(r.getFailureReason()).contains("exited with code 1");
    }

    @Test
    void sleepExceedsTimeout_timesOut() {
        LocalStepRunResult r = runner.run("sleep 8", 1, dummyStep());
        assertThat(r.isTimedOut()).isTrue();
        assertThat(r.getFailureReason()).isEqualTo("Step timed out");
    }
}
