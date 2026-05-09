-- Additive reliability migration.
-- Existing system already has: audit_events, notification_outbox(attempt_count/next_attempt_at/status).

create table if not exists audit_log (
    id uuid primary key,
    tenant_id uuid not null,
    entity_type varchar(64) not null,
    entity_id uuid not null,
    action varchar(96) not null,
    performed_by uuid,
    payload_json text,
    created_at timestamp with time zone not null default now()
);

create index if not exists ix_audit_log_tenant_entity on audit_log (tenant_id, entity_type, entity_id, created_at);
create index if not exists ix_audit_log_tenant_action on audit_log (tenant_id, action, created_at);

create table if not exists idempotency_keys (
    id uuid primary key,
    tenant_id uuid not null,
    idempotency_key varchar(256) not null,
    request_hash varchar(128) not null,
    response_json text,
    created_at timestamp with time zone not null default now(),
    constraint uq_idempotency_keys_tenant_idempotency_key unique (tenant_id, idempotency_key)
);

create index if not exists ix_idempotency_keys_tenant_created on idempotency_keys (tenant_id, created_at);

-- Compatibility columns for requested naming.
alter table if exists notification_outbox
    add column if not exists retry_count integer;

alter table if exists notification_outbox
    add column if not exists next_retry_at timestamp with time zone;

update notification_outbox
set retry_count = coalesce(retry_count, attempt_count),
    next_retry_at = coalesce(next_retry_at, next_attempt_at)
where retry_count is null or next_retry_at is null;
