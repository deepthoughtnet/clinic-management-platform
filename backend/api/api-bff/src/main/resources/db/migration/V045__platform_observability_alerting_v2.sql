create table if not exists platform_alert_rules (
    id uuid primary key,
    tenant_id uuid,
    rule_key varchar(120) not null,
    source_type varchar(80) not null,
    enabled boolean not null default true,
    severity varchar(20) not null,
    threshold_type varchar(20) not null,
    threshold_value numeric(19,4) not null,
    cooldown_minutes integer not null default 10,
    auto_resolve_enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (tenant_id, rule_key)
);

create index if not exists ix_platform_alert_rules_tenant_source
    on platform_alert_rules (tenant_id, source_type, enabled);

alter table platform_operational_alerts
    add column if not exists rule_key varchar(120),
    add column if not exists correlation_id varchar(160),
    add column if not exists source_entity_id varchar(160),
    add column if not exists occurrence_count integer not null default 1,
    add column if not exists first_seen_at timestamptz,
    add column if not exists last_seen_at timestamptz,
    add column if not exists acknowledged_by uuid,
    add column if not exists acknowledged_at timestamptz,
    add column if not exists resolved_by uuid,
    add column if not exists resolution_notes text;

update platform_operational_alerts
set first_seen_at = coalesce(first_seen_at, created_at),
    last_seen_at = coalesce(last_seen_at, created_at),
    occurrence_count = coalesce(occurrence_count, 1)
where first_seen_at is null or last_seen_at is null or occurrence_count is null;

create index if not exists ix_platform_alerts_tenant_rule_status
    on platform_operational_alerts (tenant_id, rule_key, status, last_seen_at);

create table if not exists platform_alert_escalations (
    id uuid primary key,
    alert_id uuid not null,
    escalation_level integer not null,
    escalation_status varchar(30) not null,
    escalation_target varchar(160) not null,
    escalated_at timestamptz not null default now()
);

create index if not exists ix_platform_alert_escalations_alert
    on platform_alert_escalations (alert_id, escalation_level);
