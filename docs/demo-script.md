# Demo script

End-to-end flow for presenting **workflow-orchestrator** on Kind with Kubernetes execution mode, observability, and the minimal UI.

## Prerequisites

- Docker, [kind](https://kind.sigs.k8s.io/), `kubectl`
- Three terminals (or tmux panes): backend port-forward, optional frontend dev server, `kubectl` watch

## 1. Kind cluster

```bash
kind create cluster
```

## 2. Build and load images

From the repository root:

```bash
docker build -t workflow-backend:local backend/
docker build -t workflow-python-worker:local worker/
kind load docker-image workflow-backend:local
kind load docker-image workflow-python-worker:local
```

## 3. Deploy stack

```bash
kubectl apply -f deploy/k8s/rbac.yaml
kubectl apply -f deploy/k8s/postgres.yaml
kubectl apply -f deploy/k8s/backend.yaml
kubectl apply -f deploy/k8s/prometheus.yaml
kubectl apply -f deploy/k8s/grafana.yaml
kubectl rollout status deployment/postgres --timeout=120s
kubectl rollout status deployment/backend --timeout=180s
```

## 4. Port-forwards

```bash
kubectl port-forward svc/backend 8082:8080
kubectl port-forward svc/prometheus 9090:9090
kubectl port-forward svc/grafana 3000:3000
```

Optional UI (second terminal, repo root):

```bash
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173** (proxies API to `localhost:8082` via Vite).

## 5. Create workflow

Using an example file:

```bash
curl -sS -X POST http://localhost:8082/workflows \
  -H 'Content-Type: application/json' \
  -d @examples/linear-workflow.json
```

Note the returned `id` as `WORKFLOW_ID`.

Or use the **Workflows** page: paste JSON and click **Create**.

## 6. Start execution

```bash
curl -sS -X POST "http://localhost:8082/executions?workflowId=${WORKFLOW_ID}"
```

Note `id` as `EXECUTION_ID`.

Or use **Executions** → enter workflow ID → **POST /executions**.

## 7. Watch Kubernetes

```bash
kubectl get jobs -l app=workflow-orchestrator -w
kubectl get pods -l app=workflow-orchestrator -w
```

Worker logs (replace pod name):

```bash
kubectl logs -l app=workflow-orchestrator --tail=100
```

## 8. Execution detail (UI + API)

Open **http://localhost:5173/executions/{EXECUTION_ID}** or:

```bash
curl -sS "http://localhost:8082/executions/${EXECUTION_ID}"
curl -sS "http://localhost:8082/executions/${EXECUTION_ID}/events"
```

## 9. Pause / resume / cancel

From the execution detail page, use **Pause**, **Resume**, and **Cancel**.

Or:

```bash
curl -sS -X POST "http://localhost:8082/executions/${EXECUTION_ID}/pause"
curl -sS -X POST "http://localhost:8082/executions/${EXECUTION_ID}/resume"
curl -sS -X POST "http://localhost:8082/executions/${EXECUTION_ID}/cancel"
```

## 10. Retry failed step

Create `examples/failure-retry-workflow.json` as a workflow, start an execution, wait for a step to reach **FAILED** (after retries), then:

```bash
curl -sS -X POST "http://localhost:8082/step-executions/${STEP_EXECUTION_ID}/retry"
```

Or click **Retry** on the failed row in the UI.

## 11. Grafana / Prometheus

- Prometheus UI: **http://localhost:9090** — check targets and query `workflow_executions_total`, `callbacks_received_total`, etc.
- Grafana: **http://localhost:3000** (default `admin` / `admin` from manifest) — explore Prometheus datasource and build panels (see [observability.md](observability.md)).

## 12. Metrics endpoint (backend)

```bash
curl -sS http://localhost:8082/actuator/prometheus | head
```

## Talking points

- **Control plane**: Spring Boot API + Postgres + Flyway.
- **Workers**: Kubernetes Jobs running `workflow-python-worker` calling `POST /internal/step-results`.
- **Reliability**: retries, timeouts, reconciliation, live pause/resume/cancel.
- **Observability**: Micrometer/Prometheus, execution event stream, structured logs (MDC).
