package com.tim.workflow.orchestrator.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KubernetesJobNameFactoryTest {

    private final KubernetesJobNameFactory factory = new KubernetesJobNameFactory();

    @Test
    void jobName_includesExecutionStepAndAttempt_smallIds() {
        assertThat(factory.jobName(42L, 7L, 1)).isEqualTo("workflow-exec-42-step-7-attempt-1");
        assertThat(factory.jobName(42L, 7L, 2)).contains("attempt-2");
        assertThat(factory.jobName(42L, 7L, 1).length()).isLessThanOrEqualTo(63);
    }

    @Test
    void jobName_fallsBackToCompactForm_whenTooLong() {
        long big = Long.MAX_VALUE;
        String name = factory.jobName(big, big, 999);
        assertThat(name.length()).isLessThanOrEqualTo(63);
        assertThat(name).contains("a999");
    }

    @Test
    void sanitize_stripsInvalidCharacters() {
        assertThat(factory.sanitize("WF@@TEST--")).isEqualTo("wf-test");
    }
}
