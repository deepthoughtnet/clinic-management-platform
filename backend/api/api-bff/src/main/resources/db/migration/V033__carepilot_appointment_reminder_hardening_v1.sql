alter table carepilot_campaign_executions
    add column if not exists source_type varchar(32),
    add column if not exists source_reference_id uuid,
    add column if not exists reminder_window varchar(24),
    add column if not exists reference_datetime timestamp with time zone;

create index if not exists ix_cp_exec_tenant_source_window
    on carepilot_campaign_executions (tenant_id, campaign_id, source_reference_id, reminder_window, channel_type);

create unique index if not exists ux_cp_exec_reminder_window
    on carepilot_campaign_executions (tenant_id, campaign_id, source_reference_id, reminder_window, channel_type);
