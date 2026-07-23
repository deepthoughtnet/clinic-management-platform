alter table tenant_notification_settings
    add column if not exists notification_policy_json jsonb not null default '{}'::jsonb;
