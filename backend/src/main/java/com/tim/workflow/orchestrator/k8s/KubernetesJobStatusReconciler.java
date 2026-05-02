package com.tim.workflow.orchestrator.k8s;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reserved for future reconciliation of Job/Pod phase when callbacks are missed (watch/informer).
 */
@Component
@ConditionalOnProperty(prefix = "orchestrator.execution", name = "mode", havingValue = "kubernetes")
public class KubernetesJobStatusReconciler {
    // TODO: optional Job/Pod watch to reconcile RUNNING steps without relying only on HTTP callbacks.
}
