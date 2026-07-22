create table if not exists module_business_events (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    event_type varchar(128) not null,
    event_version integer not null,
    occurred_at timestamptz not null,
    source_module varchar(64) not null,
    aggregate_type varchar(64) not null,
    aggregate_id uuid not null,
    correlation_id varchar(128) not null,
    causation_id varchar(128) not null,
    actor_id uuid,
    payload_json text not null,
    status varchar(32) not null,
    listener_count integer not null default 0,
    succeeded_count integer not null default 0,
    failed_count integer not null default 0,
    retry_scheduled_count integer not null default 0,
    dead_lettered_count integer not null default 0,
    last_processed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version integer not null default 0
);

create index if not exists ix_module_business_events_tenant_status
    on module_business_events (tenant_id, status);

create index if not exists ix_module_business_events_tenant_event_type
    on module_business_events (tenant_id, event_type);

create index if not exists ix_module_business_events_tenant_occurred_at
    on module_business_events (tenant_id, occurred_at);

create index if not exists ix_module_business_events_aggregate
    on module_business_events (aggregate_type, aggregate_id);

create table if not exists module_business_event_listener_jobs (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    event_id uuid not null references module_business_events(id) on delete cascade,
    event_type varchar(128) not null,
    listener_name varchar(128) not null,
    listener_module varchar(64) not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz,
    last_attempt_at timestamptz,
    processed_at timestamptz,
    dead_lettered_at timestamptz,
    last_error text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version integer not null default 0
);

create unique index if not exists uq_module_business_event_listener_jobs_event_listener
    on module_business_event_listener_jobs (event_id, listener_name);

create index if not exists ix_module_business_event_listener_jobs_tenant_status_next
    on module_business_event_listener_jobs (tenant_id, status, next_attempt_at);

create index if not exists ix_module_business_event_listener_jobs_event_id
    on module_business_event_listener_jobs (event_id);

create index if not exists ix_module_business_event_listener_jobs_tenant_listener
    on module_business_event_listener_jobs (tenant_id, listener_name);
