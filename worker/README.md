# Workflow Python worker

Sidecar-style Job container for the workflow orchestrator control plane. It runs a shell command supplied via environment variables, streams stdout/stderr to the pod logs, then POSTs the outcome to the Java API.

## Build

From the repository root:

```bash
docker build -t workflow-python-worker:local worker/
```

The Spring Boot app defaults `orchestrator.kubernetes.worker-image` to `workflow-python-worker:local`. Load the image into your cluster (e.g. kind/minikube/docker-desktop) or push to a registry and override the property.

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `EXECUTION_ID` | yes | Workflow execution id (numeric). |
| `STEP_EXECUTION_ID` | yes | Step execution id (numeric). |
| `CALLBACK_URL` | yes | Full URL to `POST` step results (e.g. `http://workflow-orchestrator:8082/internal/step-results`). |
| `CALLBACK_TOKEN` | yes | Secret sent as `X-Callback-Token`. |
| `STEP_COMMAND` | yes | Shell command string (`subprocess.run(..., shell=True)`). |
| `STEP_TIMEOUT_SECONDS` | no | Subprocess timeout in seconds (default `300`). |

## Callback payload

The worker sends JSON compatible with `StepResultRequest`:

- `executionId`, `stepExecutionId` (numbers)
- `status`: `SUCCESS` if exit code is `0`, else `FAILED`
- `message`: summary including exit code and truncated stdout/stderr, or a timeout message
- `logsRef`: always `null` in this phase

Failed HTTP callbacks are retried three times with exponential backoff (0.5s, 1s). If all retries fail, the process exits with code `1`. On successful delivery, the process exits with the subprocess exit code (for timeouts, exit code `1`).
