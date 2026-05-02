ALTER TABLE workflow_executions
    ADD COLUMN paused_at TIMESTAMP,
    ADD COLUMN cancel_requested BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN cancelled_at TIMESTAMP;
