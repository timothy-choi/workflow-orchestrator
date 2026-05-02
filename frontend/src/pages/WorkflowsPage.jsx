import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { createWorkflow, listWorkflows } from '../api.js';

const DEFAULT_JSON = `{
  "name": "my-workflow",
  "description": "Paste JSON from examples/",
  "steps": [
    {
      "name": "hello",
      "image": "busybox:latest",
      "command": "echo hello",
      "dependencies": []
    }
  ]
}`;

export default function WorkflowsPage() {
  const [workflows, setWorkflows] = useState([]);
  const [jsonText, setJsonText] = useState(DEFAULT_JSON);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setWorkflows(await listWorkflows());
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleCreate(e) {
    e.preventDefault();
    setError('');
    try {
      const payload = JSON.parse(jsonText);
      await createWorkflow(payload);
      setJsonText(DEFAULT_JSON);
      await load();
    } catch (err) {
      setError(err.message || String(err));
    }
  }

  return (
    <>
      <h1>Workflows</h1>
      <p className="muted">Create workflows from JSON (see repository <code>examples/</code>).</p>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Create workflow</h2>
        <form onSubmit={handleCreate}>
          <textarea
            className="json-input"
            value={jsonText}
            onChange={(ev) => setJsonText(ev.target.value)}
            spellCheck={false}
          />
          {error ? <p className="err">{error}</p> : null}
          <button type="submit" className="primary">
            Create
          </button>
        </form>
      </div>

      <div className="card">
        <h2 style={{ marginTop: 0 }}>All workflows</h2>
        {loading ? <p className="muted">Loading…</p> : null}
        {!loading && workflows.length === 0 ? <p className="muted">No workflows yet.</p> : null}
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Version</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {workflows.map((w) => (
              <tr key={w.id}>
                <td>{w.id}</td>
                <td>{w.name}</td>
                <td>{w.currentVersion}</td>
                <td>
                  <Link to={`/workflows/${w.id}`}>Detail</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
