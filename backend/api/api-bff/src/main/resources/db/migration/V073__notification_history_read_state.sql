alter table if exists notification_history
    add column if not exists read_at timestamp with time zone null;

create index if not exists ix_notification_history_tenant_read on notification_history (tenant_id, read_at);
