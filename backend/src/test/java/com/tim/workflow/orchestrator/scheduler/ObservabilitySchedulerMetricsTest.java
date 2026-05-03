package com.tim.workflow.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "workflow.scheduler.tick-enabled=true",
        "spring.task.scheduling.enabled=false"
})
class ObservabilitySchedulerMetricsTest {

    @Autowired
    private WorkflowScheduler workflowScheduler;

    @Test
    void tick_invokesSchedulerSweepWithoutThrowing() {
        assertThatCode(() -> workflowScheduler.tick()).doesNotThrowAnyException();
    }
}
