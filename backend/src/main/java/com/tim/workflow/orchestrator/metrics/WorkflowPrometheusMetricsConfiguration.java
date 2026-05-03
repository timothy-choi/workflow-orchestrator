package com.tim.workflow.orchestrator.metrics;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusNamingConvention;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;

/**
 * Avoids Prometheus type clashes between counters and gauges that would otherwise map to the same
 * time-series name, and renames current-state gauges for stable scrape names.
 */
@Configuration
class WorkflowPrometheusMetricsConfiguration {

    @Bean
    MeterRegistryCustomizer<PrometheusMeterRegistry> workflowPrometheusMeterNaming() {
        return registry -> registry.config().namingConvention(new WorkflowGaugePrometheusNamingConvention());
    }

    private static final class WorkflowGaugePrometheusNamingConvention extends PrometheusNamingConvention {

        @Override
        public String name(String name, Meter.Type type, String baseUnit) {
            if ("workflow.executions.paused.current".equals(name) && type == Meter.Type.GAUGE) {
                return PrometheusNaming.sanitizeMetricName("workflow_executions_paused_current");
            }
            if ("workflow.steps.failed.current".equals(name) && type == Meter.Type.GAUGE) {
                return PrometheusNaming.sanitizeMetricName("workflow_steps_failed_current");
            }
            return super.name(name, type, baseUnit);
        }
    }
}
