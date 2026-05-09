create table if not exists notification_history (
    id uuid primary key,
    tenant_id uuid not null,
    patient_id uuid null,
    event_type varchar(64) not null,
    channel varchar(32) not null,
    recipient varchar(256) not null,
    subject varchar(256) null,
    message text not null,
    status varchar(32) not null,
    failure_reason text null,
    source_type varchar(64) null,
    source_id uuid null,
    deduplication_key varchar(256) not null,
    outbox_event_id uuid null,
    attempt_count integer not null default 0,
    sent_at timestamp with time zone null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_notification_history_dedup unique (tenant_id, deduplication_key)
);

create index if not exists ix_notification_history_tenant_created on notification_history (tenant_id, created_at);
create index if not exists ix_notification_history_tenant_patient on notification_history (tenant_id, patient_id);
create index if not exists ix_notification_history_tenant_event on notification_history (tenant_id, event_type);
create index if not exists ix_notification_history_tenant_status on notification_history (tenant_id, status);
