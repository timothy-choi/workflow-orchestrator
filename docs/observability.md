# Observability (metrics, logs, execution events)

The backend exposes **Spring Boot Actuator** with **Prometheus** scrape support at `GET /actuator/prometheus`, plus **health** at `GET /actuator/health`. Execution audit data is available via `GET /executions/{executionId}/events` (events sorted by `createdAt` ascending).

## Custom Micrometer metrics

Prometheus export names follow Micrometer conventions (dots → underscores, `_total` suffix for counters, `_seconds` for timers).

| Logical metric | Labels | Notes |
|----------------|--------|--------|
| `workflow_executions_total` | `workflowId`, `finalStatus` | Incremented when an execution reaches a terminal status (`SUCCEEDED`, `FAILED`, `CANCELLED`). |
| `workflow_execution_duration_seconds` | `workflowId` | Histogram/timer from execution `createdAt` to terminal `finishedAt`. |
| `step_executions_total` | `stepName`, `status` | Terminal step outcomes (`SUCCESS`, `FAILED`, `CANCELLED`). |
| `step_execution_duration_seconds` | `stepName`, `status` | Duration from step `startedAt` to completion; `RETRY_WAIT` records the failed running attempt before backoff. |
| `step_retries_total` | `stepName` | Automatic retries and manual retry requests. |
| `active_workflow_executions` | — | Gauge: executions in `CREATED`, `RUNNING`, or `PAUSED`. |
| `scheduler_loop_duration_seconds` | — | One observation per scheduler tick (wall time for the full pass). |
| `kubernetes_job_create_failures_total` | — | Kubernetes API failures when creating a Job (Kubernetes mode only). |
| `callbacks_received_total` | `status` | HTTP callbacks processed (`SUCCESS`, `FAILED`). |
| `callbacks_ignored_total` | `reason` | Ignored callbacks (for example `EXECUTION_CANCELLED`, `STEP_CANCELLED`). |

## Grafana dashboard panels (suggested)

Use the Prometheus datasource pointed at the in-cluster Prometheus service (see `deploy/k8s/grafana.yaml`). Example PromQL ideas:

1. **Workflow success rate** — Ratio of succeeded executions to terminal executions over a window, for example:
   - `sum(rate(workflow_executions_total{finalStatus="SUCCEEDED"}[5m])) / sum(rate(workflow_executions_total[5m]))`
2. **Average / p95 workflow duration** — `histogram_quantile(0.95, sum(rate(workflow_execution_duration_seconds_bucket[5m])) by (le))` and `rate(workflow_execution_duration_seconds_sum[5m]) / rate(workflow_execution_duration_seconds_count[5m])`.
3. **Step failure rate** — `sum(rate(step_executions_total{status="FAILED"}[5m])) / sum(rate(step_executions_total[5m]))`.
4. **Retries by step** — `sum by (stepName) (rate(step_retries_total[5m]))`.
5. **Active executions** — `active_workflow_executions`.
6. **Scheduler loop duration** — p95 of `scheduler_loop_duration_seconds` via histogram quantile.
7. **Kubernetes Job create failures** — `rate(kubernetes_job_create_failures_total[5m])`.
8. **Callback volume** — `sum by (status) (rate(callbacks_received_total[5m]))` and `sum by (reason) (rate(callbacks_ignored_total[5m]))`.

The bundled Grafana dashboard ConfigMap is an empty **starter** dashboard; add panels in the UI or replace the JSON with a full dashboard.

## Structured logs

Console logs include MDC fields when set: `executionId`, `stepExecutionId`, `workflowId`, `k8sJobName`, `eventType`. The pattern is configured in `backend/src/main/resources/application.yaml` under `logging.pattern.console`.

## Kubernetes

Apply Prometheus and Grafana manifests (see root `README.md`), then port-forward to explore metrics and build dashboards.
