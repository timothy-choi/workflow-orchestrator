package com.tim.workflow.orchestrator.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KubernetesJobNameFactoryTest {

    private final KubernetesJobNameFactory factory = new KubernetesJobNameFactory();

    @Test
    void jobName_isDeterministicAndWithinDnsLimit() {
        assertThat(factory.jobName(42L, 7L)).isEqualTo("wf-e2a-s7");
        assertThat(factory.jobName(42L, 7L).length()).isLessThanOrEqualTo(63);
    }

    @Test
    void sanitize_stripsInvalidCharacters() {
        assertThat(factory.sanitize("WF@@TEST--")).isEqualTo("wf-test");
    }
}
