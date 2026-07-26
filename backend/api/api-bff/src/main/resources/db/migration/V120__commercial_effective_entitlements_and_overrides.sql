create table if not exists commercial_tenant_entitlement_overrides (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    subscription_id uuid references commercial_tenant_subscriptions(id),
    target_type varchar(32) not null,
    target_code varchar(64) not null,
    operation varchar(32) not null,
    value varchar(256),
    addon_state varchar(64),
    effective_from date not null,
    effective_until date,
    status varchar(32) not null,
    reason varchar(1000),
    internal_notes varchar(2000),
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    version bigint not null default 0,
    constraint ck_commercial_entitlement_overrides_target_type check (target_type in ('CAPABILITY', 'MODULE', 'FEATURE', 'LIMIT', 'ADD_ON')),
    constraint ck_commercial_entitlement_overrides_operation check (operation in ('ENABLE', 'DISABLE', 'SET_VALUE', 'SET_UNLIMITED', 'SET_ADDON_STATE')),
    constraint ck_commercial_entitlement_overrides_status check (status in ('DRAFT', 'ACTIVE', 'SCHEDULED', 'EXPIRED', 'CANCELLED')),
    constraint ck_commercial_entitlement_overrides_dates check (effective_until is null or effective_until > effective_from)
);

create index if not exists ix_commercial_entitlement_overrides_tenant_status
    on commercial_tenant_entitlement_overrides (tenant_id, status);

create index if not exists ix_commercial_entitlement_overrides_subscription
    on commercial_tenant_entitlement_overrides (subscription_id);

create index if not exists ix_commercial_entitlement_overrides_target
    on commercial_tenant_entitlement_overrides (tenant_id, target_type, target_code);

create index if not exists ix_commercial_entitlement_overrides_effective_from
    on commercial_tenant_entitlement_overrides (tenant_id, effective_from);

create index if not exists ix_commercial_entitlement_overrides_effective_until
    on commercial_tenant_entitlement_overrides (tenant_id, effective_until);

create unique index if not exists uq_commercial_entitlement_overrides_active_window
    on commercial_tenant_entitlement_overrides (tenant_id, target_type, target_code, operation, effective_from, coalesce(effective_until, date '9999-12-31'))
    where status in ('ACTIVE', 'SCHEDULED');

create table if not exists commercial_effective_entitlement_snapshots (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    subscription_id uuid references commercial_tenant_subscriptions(id),
    plan_template_id uuid references commercial_plan_templates(id),
    published_version_id uuid references commercial_plan_versions(id),
    published_version_number integer,
    subscription_status varchar(32),
    effective_from timestamp with time zone not null,
    effective_until timestamp with time zone,
    snapshot_status varchar(32) not null,
    canonical_snapshot_json jsonb not null,
    source_hash varchar(128),
    content_hash varchar(128) not null,
    generation_reason varchar(64) not null,
    validation_state varchar(32) not null,
    validation_findings_json jsonb,
    generated_at timestamp with time zone not null,
    generated_by varchar(128),
    superseded_at timestamp with time zone,
    version bigint not null default 0,
    constraint ck_commercial_effective_snapshots_status check (snapshot_status in ('CURRENT', 'SUPERSEDED', 'INVALID', 'PENDING_REGENERATION')),
    constraint ck_commercial_effective_snapshots_reason check (generation_reason in ('SUBSCRIPTION_ACTIVATED', 'SUBSCRIPTION_RESUMED', 'SUBSCRIPTION_REPLACED', 'SUBSCRIPTION_CANCELLED', 'SUBSCRIPTION_EXPIRED', 'OVERRIDE_CREATED', 'OVERRIDE_UPDATED', 'OVERRIDE_RETIRED', 'MANUAL_REGENERATE', 'BACKFILL', 'SHADOW_COMPARE')),
    constraint ck_commercial_effective_snapshots_validation_state check (validation_state in ('VALID', 'INVALID', 'PENDING'))
);

create index if not exists ix_commercial_effective_snapshots_tenant_status
    on commercial_effective_entitlement_snapshots (tenant_id, snapshot_status);

create index if not exists ix_commercial_effective_snapshots_subscription
    on commercial_effective_entitlement_snapshots (subscription_id);

create index if not exists ix_commercial_effective_snapshots_version
    on commercial_effective_entitlement_snapshots (published_version_id);

create index if not exists ix_commercial_effective_snapshots_content_hash
    on commercial_effective_entitlement_snapshots (content_hash);

create unique index if not exists uq_commercial_effective_snapshots_current_per_tenant
    on commercial_effective_entitlement_snapshots (tenant_id)
    where snapshot_status = 'CURRENT';

create table if not exists commercial_effective_entitlement_events (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    subscription_id uuid references commercial_tenant_subscriptions(id),
    snapshot_id uuid references commercial_effective_entitlement_snapshots(id),
    override_id uuid references commercial_tenant_entitlement_overrides(id),
    event_type varchar(128) not null,
    generation_reason varchar(64),
    validation_state varchar(32),
    payload_json jsonb not null,
    occurred_at timestamp with time zone not null,
    actor varchar(128),
    version bigint not null default 0
);

create index if not exists ix_commercial_effective_entitlement_events_tenant
    on commercial_effective_entitlement_events (tenant_id, occurred_at);

create index if not exists ix_commercial_effective_entitlement_events_snapshot
    on commercial_effective_entitlement_events (snapshot_id);

create index if not exists ix_commercial_effective_entitlement_events_override
    on commercial_effective_entitlement_events (override_id);
