ALTER TABLE carepilot_ai_call_executions
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suppression_reason VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failover_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS failover_reason TEXT;

CREATE INDEX IF NOT EXISTS ix_cp_ai_call_exec_tenant_next_retry
    ON carepilot_ai_call_executions (tenant_id, next_retry_at);

ALTER TABLE carepilot_ai_call_transcripts
    ADD COLUMN IF NOT EXISTS intent VARCHAR(64),
    ADD COLUMN IF NOT EXISTS escalation_reason TEXT,
    ADD COLUMN IF NOT EXISTS extracted_entities_json TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

UPDATE carepilot_ai_call_transcripts
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE carepilot_ai_call_transcripts
    ALTER COLUMN updated_at SET NOT NULL;

CREATE TABLE IF NOT EXISTS carepilot_ai_call_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    execution_id UUID,
    provider_name VARCHAR(64) NOT NULL,
    provider_call_id VARCHAR(128),
    event_type VARCHAR(48) NOT NULL,
    external_status VARCHAR(64),
    internal_status VARCHAR(32),
    event_timestamp TIMESTAMPTZ NOT NULL,
    raw_payload_redacted TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_cp_ai_call_events_tenant_execution_created
    ON carepilot_ai_call_events (tenant_id, execution_id, created_at);
CREATE INDEX IF NOT EXISTS ix_cp_ai_call_events_tenant_provider_call
    ON carepilot_ai_call_events (tenant_id, provider_call_id);
CREATE INDEX IF NOT EXISTS ix_cp_ai_call_events_tenant_created
    ON carepilot_ai_call_events (tenant_id, created_at);
