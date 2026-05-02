# workflow-orchestrator

Spring Boot control plane for workflow definitions and executions, with optional **local** step execution or **Kubernetes Job** dispatch and a **Python worker** that runs shell commands and callbacks to `POST /internal/step-results`.

## Run on Kind (local Kubernetes)

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

More detail and troubleshooting: [deploy/kind/README.md](deploy/kind/README.md).

### Smoke test

With port-forward running, create a workflow and an execution. The scheduler dispatches a Kubernetes Job using `workflow-python-worker:local`; the worker runs `STEP_COMMAND` and calls back so the step moves to **SUCCESS**.

Create a workflow (response JSON includes `id`; below assumes workflow id `1`):

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
