create table if not exists platform_operational_alerts (
    id uuid primary key,
    tenant_id uuid,
    alert_type varchar(80) not null,
    severity varchar(20) not null,
    source varchar(80) not null,
    message text not null,
    status varchar(20) not null,
    created_at timestamptz not null default now(),
    resolved_at timestamptz
);

create index if not exists ix_platform_alerts_tenant_status_created
    on platform_operational_alerts (tenant_id, status, created_at);

create index if not exists ix_platform_alerts_source_created
    on platform_operational_alerts (source, created_at);
