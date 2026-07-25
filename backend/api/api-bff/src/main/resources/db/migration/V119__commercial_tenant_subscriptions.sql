create table commercial_tenant_subscriptions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    plan_template_id uuid not null references commercial_plan_templates(id),
    published_version_id uuid not null references commercial_plan_versions(id),
    subscription_status varchar(32) not null,
    start_date date not null,
    end_date date,
    auto_renew boolean not null default false,
    display_name varchar(160),
    reference_number varchar(64),
    notes varchar(1000),
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    version bigint not null default 0,
    constraint ck_commercial_tenant_subscriptions_status check (subscription_status in ('DRAFT', 'ACTIVE', 'SCHEDULED', 'PAUSED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED')),
    constraint ck_commercial_tenant_subscriptions_dates check (end_date is null or end_date >= start_date)
);

create index ix_commercial_tenant_subscriptions_tenant
    on commercial_tenant_subscriptions (tenant_id);

create index ix_commercial_tenant_subscriptions_published_version
    on commercial_tenant_subscriptions (published_version_id);

create index ix_commercial_tenant_subscriptions_status
    on commercial_tenant_subscriptions (subscription_status);

create index ix_commercial_tenant_subscriptions_history
    on commercial_tenant_subscriptions (tenant_id, created_at desc);

create table commercial_subscription_events (
    id uuid primary key,
    subscription_id uuid not null references commercial_tenant_subscriptions(id),
    event_type varchar(64) not null,
    previous_status varchar(32),
    new_status varchar(32) not null,
    performed_by uuid,
    performed_at timestamp with time zone not null,
    remarks varchar(1000)
);

create index ix_commercial_subscription_events_subscription
    on commercial_subscription_events (subscription_id);

create index ix_commercial_subscription_events_performed_at
    on commercial_subscription_events (subscription_id, performed_at desc);
