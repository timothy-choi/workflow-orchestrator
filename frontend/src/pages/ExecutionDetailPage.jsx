import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import ExecutionTimeline from '../components/ExecutionTimeline.jsx';
import {
  cancelExecution,
  getExecution,
  getExecutionEvents,
  pauseExecution,
  resumeExecution,
  retryStep,
} from '../api.js';

/** Backend sends StepExecutionStatus as JSON string, e.g. "FAILED". */
function isStepFailed(step) {
  return String(step?.status ?? '').trim().toUpperCase() === 'FAILED';
}

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

  function execBadgeClass(s) {
    const x = String(s ?? '').toLowerCase();
    if (x === 'succeeded') return 'ok';
    if (x === 'failed' || x === 'cancelled') return 'bad';
    if (x === 'running' || x === 'created') return 'run';
    if (x === 'paused') return 'warn';
    return 'neutral';
  }

  return (
    <>
      <p>
        <Link to="/executions">← Executions</Link>
      </p>

      <header className="page-header">
        <h1>Execution {exec.id}</h1>
        <p className="muted exec-meta">
          <span className={`badge badge-status badge-${execBadgeClass(status)}`}>{exec.status}</span>
          <span>Workflow {exec.workflowId}</span>
          <span>createdAt {fmt(exec.createdAt)}</span>
          <span>finishedAt {fmt(exec.finishedAt)}</span>
          <span>updated {fmt(exec.updatedAt)}</span>
        </p>
      </header>

      {error ? <p className="err">{error}</p> : null}

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Controls</h2>
        <div className="controls-bar">
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
        <p className="muted" style={{ marginBottom: 0, fontSize: 13 }}>
          Retry appears per failed step in the table below.
        </p>
      </div>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Steps</h2>
        <div className="table-wrap">
          <table className="data-table">
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
                      disabled={busy || !isStepFailed(s)}
                      onClick={() => run(() => retryStep(s.id))}
                    >
                      Retry failed step
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Status timeline</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          Events in chronological order (same source as the events list).
        </p>
        <ExecutionTimeline events={events} formatTime={fmt} />
      </div>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Events ({events.length})</h2>
        {events.length === 0 ? (
          <p className="muted">No events.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Type</th>
                  <th>Payload</th>
                </tr>
              </thead>
              <tbody>
                {events.map((e) => (
                  <tr key={e.id}>
                    <td className="nowrap">{fmt(e.createdAt)}</td>
                    <td>
                      <code className="event-type">{e.eventType}</code>
                    </td>
                    <td className="payload-cell">{e.payload || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
