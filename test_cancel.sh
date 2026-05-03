#!/usr/bin/env bash

set -e

echo "🚀 Creating workflow..."

WORKFLOW_ID=$(curl -s -X POST http://localhost:8082/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cancel-e2e-test",
    "description": "Full cancel verification",
    "steps": [
      {
        "name": "long-step",
        "image": "workflow-python-worker:local",
        "command": "echo start && sleep 300 && echo done",
        "dependencies": [],
        "timeoutSeconds": 600,
        "maxRetries": 0
      }
    ]
  }' | jq -r '.id')

echo "✅ Workflow created: $WORKFLOW_ID"

echo "🚀 Starting execution..."

EXEC_ID=$(curl -s -X POST "http://localhost:8082/executions?workflowId=$WORKFLOW_ID" | jq -r '.id')

echo "✅ Execution started: $EXEC_ID"

echo "⏳ Waiting for Kubernetes job to start..."

for i in {1..30}; do
  POD=$(kubectl get pods --no-headers | grep "workflow-exec-$EXEC_ID" | awk '{print $1}' || true)

  if [[ -n "$POD" ]]; then
    STATUS=$(kubectl get pod "$POD" -o jsonpath='{.status.phase}')
    echo "   Pod: $POD Status: $STATUS"

    if [[ "$STATUS" == "Running" ]]; then
      echo "✅ Pod is RUNNING"
      break
    fi
  fi

  sleep 1
done

if [[ -z "$POD" ]]; then
  echo "❌ Pod never started"
  exit 1
fi

echo "🛑 Cancelling execution..."

curl -s -X POST http://localhost:8082/executions/$EXEC_ID/cancel | jq || true

echo "⏳ Waiting 3 seconds..."
sleep 3

echo "🔍 Checking execution state..."
curl -s http://localhost:8082/executions/$EXEC_ID | jq

echo "🔍 Checking events..."
curl -s http://localhost:8082/executions/$EXEC_ID/events | jq

echo "🔍 Checking Kubernetes jobs..."
kubectl get jobs

echo "🔍 Checking Kubernetes pods..."
kubectl get pods

echo "🔍 Checking backend logs..."
kubectl logs deploy/backend --tail=100

echo "🎯 DONE"