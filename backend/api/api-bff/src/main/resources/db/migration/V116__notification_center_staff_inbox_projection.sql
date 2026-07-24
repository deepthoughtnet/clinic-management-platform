create table staff_notifications (
    id uuid primary key,
    tenant_id uuid not null,
    source_event_id uuid not null,
    source_event_type varchar(120) not null,
    source_module varchar(80) not null,
    aggregate_type varchar(80) not null,
    aggregate_id uuid null,
    category varchar(32) not null,
    priority varchar(16) not null,
    title varchar(255) not null,
    preview text not null,
    business_reference varchar(160) null,
    action_label varchar(120) null,
    action_route varchar(120) null,
    action_target_id uuid null,
    correlation_id varchar(160) null,
    causation_id varchar(160) null,
    occurred_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_staff_notifications_tenant_event unique (tenant_id, source_event_id)
);

create index ix_staff_notifications_tenant_created on staff_notifications (tenant_id, created_at desc);
create index ix_staff_notifications_tenant_category on staff_notifications (tenant_id, category, created_at desc);
create index ix_staff_notifications_tenant_priority on staff_notifications (tenant_id, priority, created_at desc);
create index ix_staff_notifications_tenant_module on staff_notifications (tenant_id, source_module, created_at desc);

create table staff_notification_recipients (
    id uuid primary key,
    tenant_id uuid not null,
    staff_notification_id uuid not null references staff_notifications(id) on delete cascade,
    app_user_id uuid not null,
    recipient_display_name varchar(256) null,
    recipient_role varchar(80) null,
    matched_audience varchar(240) not null,
    title varchar(255) not null,
    preview text not null,
    category varchar(32) not null,
    priority varchar(16) not null,
    business_reference varchar(160) null,
    action_label varchar(120) null,
    action_route varchar(120) null,
    action_target_id uuid null,
    source_event_id uuid not null,
    source_event_type varchar(120) not null,
    source_module varchar(80) not null,
    aggregate_type varchar(80) not null,
    aggregate_id uuid null,
    correlation_id varchar(160) null,
    causation_id varchar(160) null,
    occurred_at timestamptz not null,
    read_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_staff_notification_recipient unique (tenant_id, staff_notification_id, app_user_id)
);

create index ix_staff_notification_recipients_tenant_user_created on staff_notification_recipients (tenant_id, app_user_id, created_at desc);
create index ix_staff_notification_recipients_tenant_user_read on staff_notification_recipients (tenant_id, app_user_id, read_at, created_at desc);
create index ix_staff_notification_recipients_tenant_created on staff_notification_recipients (tenant_id, created_at desc);
create index ix_staff_notification_recipients_tenant_category on staff_notification_recipients (tenant_id, category, created_at desc);
create index ix_staff_notification_recipients_tenant_priority on staff_notification_recipients (tenant_id, priority, created_at desc);
create index ix_staff_notification_recipients_tenant_unread on staff_notification_recipients (tenant_id, app_user_id, read_at);
