package com.tim.workflow.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@SpringBootTest
@TestPropertySource(properties = {
        "workflow.scheduler.tick-enabled=true",
        "spring.task.scheduling.enabled=false"
})
class ObservabilitySchedulerMetricsTest {

    @Autowired
    private WorkflowScheduler workflowScheduler;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void tick_recordsSchedulerLoopDuration() {
        Timer timer = meterRegistry.get("scheduler.loop.duration").timer();
        long before = timer.count();
        workflowScheduler.tick();
        assertThat(timer.count()).isGreaterThan(before);
    }
}
