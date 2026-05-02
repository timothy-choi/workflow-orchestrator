# workflow-orchestrator

**A small Spring Boot control plane for DAG workflows**: define versions in Postgres, run steps **locally** or as **Kubernetes Jobs** with a **Python worker**, collect callbacks, add retries/timeouts/reconciliation, expose pause/resume/cancel/manual retry, and ship metrics plus a **minimal React UI** for demos.

---

## Architecture (text)

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────────┐
│  React UI       │     │  Spring Boot API │     │  PostgreSQL + Flyway       │
│  (optional)     │────▶│  /workflows      │────▶│  workflows, executions,    │
│  localhost:5173 │     │  /executions     │     │  steps, events             │
└─────────────────┘     │  /actuator/*     │     └─────────────────────────┘
        │               └────────┬─────────┘
        │                        │
        │                        │ scheduler / dispatch
        │                        ▼
        │               ┌──────────────────┐         ┌─────────────────────┐
        │               │  Kubernetes      │────────▶│  Job + worker pod      │
        └──────────────▶│  Jobs (opt.)     │ callback│  POST /internal/…      │
                        └──────────────────┘────────▶└─────────────────────┘
                                      │
                                      ▼
                        ┌──────────────────┐
                        │ Prometheus       │
                        │ Grafana (opt.)   │
                        └──────────────────┘
```

---

## Quick examples (resume bullets)

- **Linear DAG**: `examples/linear-workflow.json` — strict A → B → C.
- **Parallel branches**: `examples/parallel-workflow.json` — independent steps.
- **Fan-in**: `examples/fan-in-workflow.json` — merge after A and B.
- **Failures & retries**: `examples/failure-retry-workflow.json` — non-zero exit with `maxRetries` (best observed in **Kubernetes** mode with the Python worker).

Full guided demo: [docs/demo-script.md](docs/demo-script.md).

---

## Minimal dashboard (frontend)

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. Vite proxies `/workflows`, `/executions`, and `/step-executions` to **http://localhost:8082** (override with `VITE_PROXY_TARGET`).

**Screenshots:** add PNGs under [docs/images/](docs/images/README.md) (placeholders described there).

---

## Local backend (Docker Postgres recommended)

Default datasource in `backend/src/main/resources/application.yaml` targets `localhost:5433`. Start Postgres (example):

```bash
docker run --name workflow-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=workflow_orchestrator \
  -p 5433:5432 -d postgres:16
```

Run API:

```bash
cd backend && ./mvnw spring-boot:run
```

API base (default): **http://localhost:8082**

CORS for the UI is enabled for `http://localhost:5173` (see `orchestrator.cors.allowed-origins`).

---

## HTTP API (surface used by the UI)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/workflows` | List workflows |
| POST | `/workflows` | Create workflow (JSON body) |
| GET | `/workflows/{id}` | Workflow detail + definition JSON |
| GET | `/executions` | Recent executions (`?limit=` optional, max 200) |
| POST | `/executions?workflowId=` | Start execution |
| GET | `/executions/{id}` | Execution detail (steps + embedded events) |
| GET | `/executions/{id}/events` | Events ordered by time |
| POST | `/executions/{id}/pause` | Pause |
| POST | `/executions/{id}/resume` | Resume |
| POST | `/executions/{id}/cancel` | Cancel |
| POST | `/step-executions/{id}/retry` | Manual retry of **FAILED** step |

### curl snippets

Create workflow from example:

```bash
curl -sS -X POST http://localhost:8082/workflows \
  -H 'Content-Type: application/json' \
  -d @examples/fan-in-workflow.json
```

Start execution (replace workflow id):

```bash
curl -sS -X POST 'http://localhost:8082/executions?workflowId=1'
```

List executions:

```bash
curl -sS 'http://localhost:8082/executions?limit=20'
```

Metrics:

```bash
curl -sS http://localhost:8082/actuator/prometheus | head
```

---

## Run on Kind (Kubernetes mode)

### Prerequisites

- Docker, [kind](https://kind.sigs.k8s.io/docs/user/quick-start/), [kubectl](https://kubernetes.io/docs/tasks/tools/)

### 1. Create a cluster

```bash
kind create cluster
```

Use `kind create cluster --name <name>` if you prefer a named cluster; pass the same `--name` when loading images.

### 2. Build images

From the repository root:

```bash
docker build -t workflow-backend:local backend/
docker build -t workflow-python-worker:local worker/
```

### 3. Load images into Kind

```bash
kind load docker-image workflow-backend:local
kind load docker-image workflow-python-worker:local
```

### 4. Apply manifests (order)

```bash
kubectl apply -f deploy/k8s/rbac.yaml
kubectl apply -f deploy/k8s/postgres.yaml
kubectl apply -f deploy/k8s/backend.yaml
kubectl apply -f deploy/k8s/prometheus.yaml
kubectl apply -f deploy/k8s/grafana.yaml
```

Wait until Postgres and the backend are ready:

```bash
kubectl rollout status deployment/postgres --timeout=120s
kubectl rollout status deployment/backend --timeout=180s
```

### 5. Reach the API from your machine

The Service exposes port **8080** in the cluster. Port-forward to **8082** locally (matches the default dev port in `application.yaml`):

```bash
kubectl port-forward svc/backend 8082:8080
```

Optional observability stack (Prometheus scrapes `http://backend:8080/actuator/prometheus`; Grafana provisions a Prometheus datasource):

```bash
kubectl port-forward svc/prometheus 9090:9090
kubectl port-forward svc/grafana 3000:3000
```

Default Grafana login in the manifest is **admin** / **admin**.

Metrics panel ideas and PromQL hints: [docs/observability.md](docs/observability.md).

More detail and troubleshooting: [deploy/kind/README.md](deploy/kind/README.md).

### Smoke test (Kubernetes)

With port-forward running, create a workflow and an execution. The scheduler dispatches a Kubernetes Job using `workflow-python-worker:local`; the worker runs `STEP_COMMAND` and calls back so the step moves to **SUCCESS**.

```bash
curl -sS -X POST http://localhost:8082/workflows \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "kind-smoke",
    "description": "Kind smoke test",
    "steps": [
      {
        "name": "echo",
        "image": "unused-in-kubernetes-mode",
        "command": "echo hello-from-worker",
        "dependencies": []
      }
    ]
  }'
```

Start an execution (replace `workflowId` if yours is not `1`):

```bash
curl -sS -X POST 'http://localhost:8082/executions?workflowId=1'
```

Poll execution status (replace `1` with the execution `id` from the previous response):

```bash
curl -sS http://localhost:8082/executions/1
```

Inspect Jobs and pods created for the step:

```bash
kubectl get jobs -l app=workflow-orchestrator
kubectl get pods -l app=workflow-orchestrator
```

Worker pod logs (replace `<pod-name>` with a pod name from `kubectl get pods`; alternatively use the Job name pattern `wf-e<id>-s<step-exec-id>` in hex):

```bash
kubectl logs <pod-name>
```

### Callback token

Cluster manifests set `ORCHESTRATOR_CALLBACK_TOKEN=dev-callback-token`. The worker receives the same value as `CALLBACK_TOKEN` on the Job.

---

## UI placeholders

Until screenshots are checked in, see [docs/images/README.md](docs/images/README.md) for suggested filenames (`workflows-ui.png`, `executions-ui.png`, `execution-detail-ui.png`).

---

## Documentation index

- [docs/demo-script.md](docs/demo-script.md) — presenter walkthrough
- [docs/observability.md](docs/observability.md) — metrics & Grafana
- [docs/images/README.md](docs/images/README.md) — screenshot placeholders
