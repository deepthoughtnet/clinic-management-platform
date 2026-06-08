create table if not exists careai_receptionist_tasks (
    id uuid primary key,
    tenant_id uuid not null,
    conversation_id uuid,
    workflow_id uuid,
    patient_id uuid,
    lead_id uuid,
    appointment_id uuid,
    task_type varchar(64) not null,
    status varchar(32) not null,
    priority varchar(32) not null,
    channel varchar(64),
    reason varchar(255),
    latest_user_message text,
    callback_time_pref varchar(128),
    callback_due_at timestamptz,
    assigned_user_id uuid,
    assigned_at timestamptz,
    resolved_at timestamptz,
    resolved_by_user_id uuid,
    resolution_notes text,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_careai_receptionist_tasks_conversation
        foreign key (conversation_id) references careai_conversations(id) on delete set null,
    constraint fk_careai_receptionist_tasks_workflow
        foreign key (workflow_id) references careai_workflows(id) on delete set null
);

create index if not exists ix_careai_receptionist_tasks_tenant_status_priority_created
    on careai_receptionist_tasks (tenant_id, status, priority, created_at);

create index if not exists ix_careai_receptionist_tasks_tenant_type_status
    on careai_receptionist_tasks (tenant_id, task_type, status);

create index if not exists ix_careai_receptionist_tasks_tenant_assigned_status
    on careai_receptionist_tasks (tenant_id, assigned_user_id, status);

create index if not exists ix_careai_receptionist_tasks_tenant_patient
    on careai_receptionist_tasks (tenant_id, patient_id);

create index if not exists ix_careai_receptionist_tasks_tenant_conversation
    on careai_receptionist_tasks (tenant_id, conversation_id);

create table if not exists careai_receptionist_task_events (
    id uuid primary key,
    tenant_id uuid not null,
    task_id uuid not null references careai_receptionist_tasks(id) on delete cascade,
    event_type varchar(64) not null,
    actor_user_id uuid,
    payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index if not exists ix_careai_receptionist_task_events_tenant_task_created
    on careai_receptionist_task_events (tenant_id, task_id, created_at);
