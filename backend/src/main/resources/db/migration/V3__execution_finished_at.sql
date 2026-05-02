ALTER TABLE workflow_executions
    ADD COLUMN finished_at TIMESTAMP NULL;

CREATE INDEX idx_workflow_executions_status ON workflow_executions (status);
