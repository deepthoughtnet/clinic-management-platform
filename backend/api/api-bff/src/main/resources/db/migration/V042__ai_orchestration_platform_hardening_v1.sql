create table if not exists ai_prompt_definitions (
    id uuid primary key,
    tenant_id uuid,
    prompt_key varchar(160) not null,
    name varchar(200) not null,
    description text,
    domain varchar(80),
    use_case varchar(120),
    active_version integer,
    is_system_prompt boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid
);

create unique index if not exists ux_ai_prompt_definitions_scope_key
    on ai_prompt_definitions (coalesce(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), prompt_key);
create index if not exists ix_ai_prompt_definitions_tenant_domain on ai_prompt_definitions (tenant_id, domain);
create index if not exists ix_ai_prompt_definitions_tenant_use_case on ai_prompt_definitions (tenant_id, use_case);

create table if not exists ai_prompt_versions (
    id uuid primary key,
    prompt_definition_id uuid not null references ai_prompt_definitions(id) on delete cascade,
    version integer not null,
    status varchar(16) not null,
    model_hint varchar(80),
    temperature numeric(6,4),
    max_tokens integer,
    system_prompt text not null,
    user_prompt_template text not null,
    variables_json text,
    guardrail_profile varchar(120),
    created_at timestamptz not null,
    activated_at timestamptz
);

create unique index if not exists ux_ai_prompt_versions_definition_version
    on ai_prompt_versions (prompt_definition_id, version);
create unique index if not exists ux_ai_prompt_versions_definition_active
    on ai_prompt_versions (prompt_definition_id) where status = 'ACTIVE';

create table if not exists ai_invocation_logs (
    id uuid primary key,
    tenant_id uuid not null,
    request_id uuid,
    correlation_id varchar(120),
    domain varchar(80),
    use_case varchar(120),
    prompt_key varchar(160),
    prompt_version integer,
    provider_name varchar(80),
    model_name varchar(120),
    status varchar(32) not null,
    input_token_count bigint,
    output_token_count bigint,
    estimated_cost numeric(19,6),
    latency_ms bigint,
    request_payload_redacted text,
    response_payload_redacted text,
    error_code varchar(80),
    error_message text,
    created_at timestamptz not null,
    created_by uuid
);

create index if not exists ix_ai_invocation_logs_tenant_created on ai_invocation_logs (tenant_id, created_at);
create index if not exists ix_ai_invocation_logs_tenant_status on ai_invocation_logs (tenant_id, status);
create index if not exists ix_ai_invocation_logs_tenant_provider on ai_invocation_logs (tenant_id, provider_name);
create index if not exists ix_ai_invocation_logs_tenant_use_case on ai_invocation_logs (tenant_id, use_case);

create table if not exists ai_tool_definitions (
    id uuid primary key,
    tenant_id uuid,
    tool_key varchar(160) not null,
    name varchar(200) not null,
    description text,
    category varchar(80),
    enabled boolean not null default true,
    risk_level varchar(24),
    requires_approval boolean not null default false,
    input_schema_json text,
    output_schema_json text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index if not exists ux_ai_tool_definitions_scope_key
    on ai_tool_definitions (coalesce(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), tool_key);

create table if not exists ai_workflow_runs (
    id uuid primary key,
    tenant_id uuid not null,
    workflow_key varchar(160) not null,
    status varchar(24) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    failure_reason text,
    triggered_by uuid,
    input_summary text,
    output_summary text
);

create index if not exists ix_ai_workflow_runs_tenant_started on ai_workflow_runs (tenant_id, started_at);

create table if not exists ai_workflow_steps (
    id uuid primary key,
    workflow_run_id uuid not null references ai_workflow_runs(id) on delete cascade,
    step_name varchar(160) not null,
    step_type varchar(80),
    status varchar(24) not null,
    started_at timestamptz,
    completed_at timestamptz,
    provider_name varchar(80),
    tool_key varchar(160),
    error_message text
);

create index if not exists ix_ai_workflow_steps_run on ai_workflow_steps (workflow_run_id);

create table if not exists ai_guardrail_profiles (
    id uuid primary key,
    tenant_id uuid,
    profile_key varchar(160) not null,
    name varchar(200) not null,
    description text,
    enabled boolean not null default true,
    blocked_topics_json text,
    pii_redaction_enabled boolean not null default true,
    human_approval_required boolean not null default false,
    max_output_tokens integer,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index if not exists ux_ai_guardrail_profiles_scope_key
    on ai_guardrail_profiles (coalesce(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), profile_key);
