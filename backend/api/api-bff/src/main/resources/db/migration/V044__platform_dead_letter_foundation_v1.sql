create table if not exists platform_dead_letter_events (
    id uuid primary key,
    tenant_id uuid not null,
    source_type varchar(40) not null,
    source_execution_id uuid not null,
    failure_reason text,
    payload_summary text,
    retry_count integer not null default 0,
    dead_lettered_at timestamptz not null default now(),
    recovery_status varchar(30) not null,
    last_recovery_error text
);

create index if not exists ix_dlq_tenant_source_created
    on platform_dead_letter_events (tenant_id, source_type, dead_lettered_at);

create index if not exists ix_dlq_tenant_recovery
    on platform_dead_letter_events (tenant_id, recovery_status, dead_lettered_at);
