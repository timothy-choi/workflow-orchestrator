import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createExecution, listExecutions } from '../api.js';

export default function ExecutionsPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState([]);
  const [workflowId, setWorkflowId] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setRows(await listExecutions(100));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    const id = setInterval(load, 4000);
    return () => clearInterval(id);
  }, []);

  async function handleStart(e) {
    e.preventDefault();
    const id = Number(workflowId);
    if (!id) {
      setError('Enter a numeric workflow ID');
      return;
    }
    setError('');
    try {
      await createExecution(id);
      setWorkflowId('');
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  function fmt(t) {
    if (!t) return '—';
    const d = new Date(t);
    return Number.isNaN(d.getTime()) ? String(t) : d.toLocaleString();
  }

  function statusClass(status) {
    const s = String(status ?? '').toLowerCase();
    if (s === 'succeeded') return 'ok';
    if (s === 'failed' || s === 'cancelled') return 'bad';
    if (s === 'running' || s === 'created') return 'run';
    if (s === 'paused') return 'warn';
    return 'neutral';
  }

  function openRow(id) {
    navigate(`/ui/executions/${id}`);
  }

  return (
    <>
      <header className="page-header">
        <h1>Executions</h1>
        <p className="muted">Recent workflow runs (newest first). Auto-refreshes every few seconds.</p>
      </header>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Start execution</h2>
        <form onSubmit={handleStart} style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <label>
            Workflow ID{' '}
            <input
              type="number"
              min="1"
              value={workflowId}
              onChange={(ev) => setWorkflowId(ev.target.value)}
              placeholder="e.g. 1"
            />
          </label>
          <button type="submit" className="primary">
            POST /executions
          </button>
        </form>
        {error ? <p className="err">{error}</p> : null}
      </div>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Recent</h2>
        {loading ? <p className="muted">Loading…</p> : null}
        {!loading && rows.length === 0 ? <p className="muted">No executions.</p> : null}
        <table className="data-table">
          <thead>
            <tr>
              <th>Execution ID</th>
              <th>Workflow ID</th>
              <th>Status</th>
              <th>createdAt</th>
              <th>finishedAt</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr
                key={r.id}
                className="click-row"
                onClick={() => openRow(r.id)}
                title="Open execution details"
              >
                <td>{r.id}</td>
                <td>{r.workflowId}</td>
                <td>
                  <span className={`badge badge-status badge-${statusClass(r.status)}`}>{r.status}</span>
                </td>
                <td>{fmt(r.createdAt)}</td>
                <td>{fmt(r.finishedAt)}</td>
                <td onClick={(ev) => ev.stopPropagation()}>
                  <Link to={`/ui/executions/${r.id}`}>Details</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
