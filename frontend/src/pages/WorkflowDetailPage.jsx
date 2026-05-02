import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getWorkflow } from '../api.js';

export default function WorkflowDetailPage() {
  const { workflowId } = useParams();
  const [wf, setWf] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await getWorkflow(workflowId);
        if (!cancelled) setWf(data);
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [workflowId]);

  if (error) {
    return (
      <>
        <p className="err">{error}</p>
        <Link to="/workflows">← Workflows</Link>
      </>
    );
  }

  if (!wf) {
    return <p className="muted">Loading…</p>;
  }

  let pretty = wf.definitionJson;
  try {
    pretty = JSON.stringify(JSON.parse(wf.definitionJson), null, 2);
  } catch {
    /* keep raw */
  }

  return (
    <>
      <p>
        <Link to="/workflows">← Workflows</Link>
      </p>
      <h1>{wf.name}</h1>
      <p className="muted">
        ID {wf.id} · version {wf.currentVersion}
      </p>
      {wf.description ? <p>{wf.description}</p> : null}
      <div className="card">
        <h2 style={{ marginTop: 0 }}>Definition</h2>
        <pre className="events">{pretty}</pre>
      </div>
      <Link className="btn primary" to="/executions">
        Go to executions
      </Link>
    </>
  );
}
