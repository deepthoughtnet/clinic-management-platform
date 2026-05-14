CREATE TABLE IF NOT EXISTS carepilot_ai_call_campaigns (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    call_type VARCHAR(48) NOT NULL,
    status VARCHAR(24) NOT NULL,
    template_id UUID,
    channel VARCHAR(24) NOT NULL,
    retry_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    escalation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_cp_ai_call_campaigns_tenant_status ON carepilot_ai_call_campaigns (tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_cp_ai_call_campaigns_tenant_type ON carepilot_ai_call_campaigns (tenant_id, call_type);

CREATE TABLE IF NOT EXISTS carepilot_ai_call_executions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    patient_id UUID,
    lead_id UUID,
    phone_number VARCHAR(48) NOT NULL,
    execution_status VARCHAR(32) NOT NULL,
    provider_name VARCHAR(64),
    provider_call_id VARCHAR(128),
    scheduled_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    escalation_required BOOLEAN NOT NULL DEFAULT FALSE,
    escalation_reason TEXT,
    transcript_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_cp_ai_call_exec_tenant_status ON carepilot_ai_call_executions (tenant_id, execution_status);
CREATE INDEX IF NOT EXISTS ix_cp_ai_call_exec_tenant_scheduled ON carepilot_ai_call_executions (tenant_id, scheduled_at);
CREATE INDEX IF NOT EXISTS ix_cp_ai_call_exec_tenant_campaign ON carepilot_ai_call_executions (tenant_id, campaign_id);
CREATE INDEX IF NOT EXISTS ix_cp_ai_call_exec_tenant_escalation ON carepilot_ai_call_executions (tenant_id, escalation_required);

CREATE TABLE IF NOT EXISTS carepilot_ai_call_transcripts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    execution_id UUID NOT NULL,
    transcript_text TEXT,
    summary TEXT,
    sentiment VARCHAR(24),
    outcome VARCHAR(48),
    requires_follow_up BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_cp_ai_call_transcripts_tenant_execution ON carepilot_ai_call_transcripts (tenant_id, execution_id);
CREATE INDEX IF NOT EXISTS ix_cp_ai_call_transcripts_tenant_created ON carepilot_ai_call_transcripts (tenant_id, created_at);
