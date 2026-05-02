import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  cancelExecution,
  getExecution,
  getExecutionEvents,
  pauseExecution,
  resumeExecution,
  retryStep,
} from '../api.js';

export default function ExecutionDetailPage() {
  const { executionId } = useParams();
  const [exec, setExec] = useState(null);
  const [events, setEvents] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [ex, ev] = await Promise.all([getExecution(executionId), getExecutionEvents(executionId)]);
      setExec(ex);
      setEvents(ev);
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }, [executionId]);

  useEffect(() => {
    load();
    const id = setInterval(load, 2500);
    return () => clearInterval(id);
  }, [load]);

  async function run(action) {
    setBusy(true);
    setError('');
    try {
      await action();
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  function fmt(t) {
    if (!t) return '—';
    const d = new Date(t);
    return Number.isNaN(d.getTime()) ? String(t) : d.toLocaleString();
  }

  if (!exec && !error) {
    return <p className="muted">Loading…</p>;
  }

  if (error && !exec) {
    return (
      <>
        <p className="err">{error}</p>
        <Link to="/executions">← Executions</Link>
      </>
    );
  }

  const status = exec.status;
  const canPause = status === 'CREATED' || status === 'RUNNING';
  const canResume = status === 'PAUSED';
  const canCancel = status !== 'SUCCEEDED' && status !== 'CANCELLED' && status !== 'FAILED';

  return (
    <>
      <p>
        <Link to="/executions">← Executions</Link>
      </p>
      <h1>Execution {exec.id}</h1>
      <p>
        <span className="badge">{exec.status}</span>{' '}
        <span className="muted">
          workflow {exec.workflowId} · updated {fmt(exec.updatedAt)}
        </span>
      </p>
      {error ? <p className="err">{error}</p> : null}

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Controls</h2>
        <button type="button" disabled={busy || !canPause} onClick={() => run(() => pauseExecution(exec.id))}>
          Pause
        </button>
        <button type="button" disabled={busy || !canResume} onClick={() => run(() => resumeExecution(exec.id))}>
          Resume
        </button>
        <button
          type="button"
          className="danger"
          disabled={busy || !canCancel}
          onClick={() => run(() => cancelExecution(exec.id))}
        >
          Cancel
        </button>
      </div>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Steps</h2>
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Name</th>
              <th>Status</th>
              <th>Attempt</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {exec.steps?.map((s) => (
              <tr key={s.id}>
                <td>{s.stepIndex}</td>
                <td>{s.stepName}</td>
                <td>
                  <span className="badge">{s.status}</span>
                </td>
                <td>
                  {s.attempt} / retries {s.retryCount}/{s.maxRetries}
                </td>
                <td>
                  <button
                    type="button"
                    disabled={busy || s.status !== 'FAILED'}
                    onClick={() => run(() => retryStep(s.id))}
                  >
                    Retry
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Events ({events.length})</h2>
        <pre className="events">
          {(events || [])
            .map((e) => `${e.createdAt}\t${e.eventType}\t${e.payload || ''}`)
            .join('\n')}
        </pre>
      </div>
    </>
  );
}
