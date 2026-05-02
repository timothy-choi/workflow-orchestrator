async function parseJson(res) {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function apiFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body != null && headers['Content-Type'] == null) {
    headers['Content-Type'] = 'application/json';
  }
  const res = await fetch(path, {
    headers,
    ...options,
  });
  const body = await parseJson(res);
  if (!res.ok) {
    const msg = typeof body === 'string' ? body : JSON.stringify(body);
    throw new Error(msg || res.statusText);
  }
  return body;
}

export function listWorkflows() {
  return apiFetch('/workflows');
}

export function getWorkflow(id) {
  return apiFetch(`/workflows/${id}`);
}

export function createWorkflow(payload) {
  return apiFetch('/workflows', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function listExecutions(limit = 50) {
  return apiFetch(`/executions?limit=${limit}`);
}

export function getExecution(id) {
  return apiFetch(`/executions/${id}`);
}

export function getExecutionEvents(id) {
  return apiFetch(`/executions/${id}/events`);
}

export function createExecution(workflowId) {
  return apiFetch(`/executions?workflowId=${workflowId}`, { method: 'POST' });
}

export function pauseExecution(id) {
  return apiFetch(`/executions/${id}/pause`, { method: 'POST' });
}

export function resumeExecution(id) {
  return apiFetch(`/executions/${id}/resume`, { method: 'POST' });
}

export function cancelExecution(id) {
  return apiFetch(`/executions/${id}/cancel`, { method: 'POST' });
}

export function retryStep(stepExecutionId) {
  return apiFetch(`/step-executions/${stepExecutionId}/retry`, { method: 'POST' });
}
