ALTER TABLE workflow_executions
    ADD COLUMN pause_requested BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE workflow_executions
SET pause_requested = TRUE
WHERE status = 'PAUSED';
