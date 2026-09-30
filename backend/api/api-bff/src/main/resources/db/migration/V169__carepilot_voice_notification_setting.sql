alter table tenant_notification_settings
    add column if not exists voice_enabled boolean not null default false;
