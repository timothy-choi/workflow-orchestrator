import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { createExecution, listExecutions } from '../api.js';

export default function ExecutionsPage() {
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

  return (
    <>
      <h1>Executions</h1>
      <p className="muted">Recent executions (newest first). Refreshes every few seconds.</p>

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
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Workflow</th>
              <th>Status</th>
              <th>Started</th>
              <th>Finished</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>{r.workflowId}</td>
                <td>
                  <span className="badge">{r.status}</span>
                </td>
                <td>{fmt(r.createdAt)}</td>
                <td>{fmt(r.finishedAt)}</td>
                <td>
                  <Link to={`/executions/${r.id}`}>Open</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
