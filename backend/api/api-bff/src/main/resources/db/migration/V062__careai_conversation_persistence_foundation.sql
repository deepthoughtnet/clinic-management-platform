create table if not exists careai_conversations (
    id uuid primary key,
    tenant_id uuid not null,
    channel varchar(32) not null,
    patient_id uuid,
    lead_id uuid,
    appointment_id uuid,
    status varchar(32) not null,
    current_workflow_id uuid,
    external_session_id varchar(128),
    summary text,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create index if not exists ix_careai_conversations_tenant_status_updated
    on careai_conversations (tenant_id, status, updated_at);

create index if not exists ix_careai_conversations_tenant_patient
    on careai_conversations (tenant_id, patient_id);

create index if not exists ix_careai_conversations_tenant_external_session
    on careai_conversations (tenant_id, external_session_id);

create table if not exists careai_messages (
    id uuid primary key,
    tenant_id uuid not null,
    conversation_id uuid not null references careai_conversations(id) on delete cascade,
    speaker varchar(32) not null,
    channel varchar(32) not null,
    content text not null,
    intent varchar(64),
    entities_json jsonb not null default '{}'::jsonb,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index if not exists ix_careai_messages_tenant_conversation_created
    on careai_messages (tenant_id, conversation_id, created_at);

create index if not exists ix_careai_messages_tenant_created
    on careai_messages (tenant_id, created_at);

create table if not exists careai_workflows (
    id uuid primary key,
    tenant_id uuid not null,
    conversation_id uuid not null references careai_conversations(id) on delete cascade,
    workflow_type varchar(64) not null,
    state varchar(64) not null,
    context_json jsonb not null default '{}'::jsonb,
    last_question_key varchar(128),
    repeated_question_count int not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create index if not exists ix_careai_workflows_tenant_conversation_state
    on careai_workflows (tenant_id, conversation_id, state);

create index if not exists ix_careai_workflows_tenant_type_state
    on careai_workflows (tenant_id, workflow_type, state);

create table if not exists careai_workflow_events (
    id uuid primary key,
    tenant_id uuid not null,
    workflow_id uuid not null references careai_workflows(id) on delete cascade,
    event_type varchar(64) not null,
    payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index if not exists ix_careai_workflow_events_tenant_workflow_created
    on careai_workflow_events (tenant_id, workflow_id, created_at);

create table if not exists careai_pending_confirmations (
    id uuid primary key,
    tenant_id uuid not null,
    workflow_id uuid not null references careai_workflows(id) on delete cascade,
    confirmation_type varchar(64) not null,
    scope_key varchar(128) not null,
    version int not null default 1,
    prompt text,
    payload_json jsonb not null default '{}'::jsonb,
    expires_at timestamptz,
    resolved_at timestamptz,
    resolution varchar(32),
    created_at timestamptz not null
);

create index if not exists ix_careai_pending_confirmations_tenant_workflow_resolved
    on careai_pending_confirmations (tenant_id, workflow_id, resolved_at);

create index if not exists ix_careai_pending_confirmations_tenant_expires
    on careai_pending_confirmations (tenant_id, expires_at);

create table if not exists careai_session_bindings (
    id uuid primary key,
    tenant_id uuid not null,
    conversation_id uuid not null references careai_conversations(id) on delete cascade,
    transport varchar(64) not null,
    external_session_id varchar(128) not null,
    active boolean not null default true,
    active_instance_id varchar(128),
    last_seen_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists ix_careai_session_bindings_tenant_external_session
    on careai_session_bindings (tenant_id, external_session_id);

create index if not exists ix_careai_session_bindings_tenant_conversation_active
    on careai_session_bindings (tenant_id, conversation_id, active);
