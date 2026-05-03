export default function ExecutionTimeline({ events, formatTime }) {
  const list = Array.isArray(events) ? events : [];

  if (list.length === 0) {
    return <p className="muted">No events yet.</p>;
  }

  return (
    <ol className="timeline">
      {list.map((e, i) => {
        const tone = toneForEvent(e.eventType);
        return (
          <li key={e.id ?? `${e.createdAt}-${i}`} className={`timeline-item timeline-${tone}`}>
            <div className="timeline-marker" aria-hidden />
            <div className="timeline-body">
              <div className="timeline-time">{formatTime(e.createdAt)}</div>
              <div className="timeline-title">{e.eventType}</div>
              {e.payload ? (
                <div className="timeline-payload muted">{e.payload}</div>
              ) : null}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function toneForEvent(eventType) {
  const t = String(eventType ?? '');
  if (
    t.includes('SUCCEEDED') ||
    t === 'EXECUTION_RESUMED' ||
    t === 'CALLBACK_RECEIVED' ||
    t === 'STEP_RETRY_READY'
  ) {
    return 'ok';
  }
  if (
    t.includes('FAILED') ||
    t.includes('TIMEOUT') ||
    t.includes('CANCELLED') ||
    t === 'JOB_MISSING' ||
    t === 'JOB_NOT_FOUND'
  ) {
    return 'bad';
  }
  if (t.includes('PAUSED') || t.includes('RETRY_SCHEDULED') || t === 'STEP_MANUAL_RETRY_REQUESTED') {
    return 'warn';
  }
  return 'neutral';
}
