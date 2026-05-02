CREATE TABLE workflow_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows (id),
    workflow_version_id BIGINT NOT NULL REFERENCES workflow_versions (id),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workflow_executions_workflow_id ON workflow_executions (workflow_id);

CREATE TABLE step_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT NOT NULL REFERENCES workflow_executions (id) ON DELETE CASCADE,
    step_index INTEGER NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (workflow_execution_id, step_index)
);

CREATE INDEX idx_step_executions_workflow_execution_id ON step_executions (workflow_execution_id);

CREATE TABLE execution_events (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT NOT NULL REFERENCES workflow_executions (id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_execution_events_workflow_execution_id ON execution_events (workflow_execution_id);
