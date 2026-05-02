# Kind (local Kubernetes)

Use this flow to run the workflow orchestrator control plane, PostgreSQL, and worker Jobs on a single-node [kind](https://kind.sigs.k8s.io/) cluster.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/)
- [kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)

## Build container images

From the repository root:

```bash
docker build -t workflow-backend:local backend/
docker build -t workflow-python-worker:local worker/
```

The backend image runs Spring Boot on port **8080** inside the container (see `SERVER_PORT` in `deploy/k8s/backend.yaml`). The worker image is referenced by Jobs via `ORCHESTRATOR_KUBERNETES_WORKER_IMAGE`.

## Load images into Kind

Kind nodes do not see your host Docker daemon’s images unless you load them (or use a registry).

```bash
kind load docker-image workflow-backend:local
kind load docker-image workflow-python-worker:local
```

If your cluster is not the default name `kind`, pass `--name <cluster>` to both `kind create cluster` (below) and `kind load docker-image`.

## Apply manifests

See the root [README.md](../../README.md) for full apply order, port-forwarding, and smoke tests.

Troubleshooting:

- **`ImagePullBackOff` on `workflow-backend:local`**: run `kind load docker-image workflow-backend:local` again after rebuilding.
- **`Forbidden` when creating Jobs**: ensure `deploy/k8s/rbac.yaml` is applied and the backend pod uses `serviceAccountName: workflow-orchestrator-backend`.
- **Worker cannot reach callback**: `ORCHESTRATOR_CALLBACK_BASE_URL` must be a URL reachable from **worker pods** (cluster DNS), e.g. `http://backend:8080`, not `localhost`.
