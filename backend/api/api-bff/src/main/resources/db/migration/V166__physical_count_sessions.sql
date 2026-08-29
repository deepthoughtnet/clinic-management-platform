create table physical_count_sessions (
    id uuid primary key,
    tenant_id uuid not null,
    session_name varchar(256) not null,
    location_id uuid not null,
    location_name varchar(256) not null,
    scope varchar(32) not null,
    scope_label varchar(128) not null,
    reason varchar(64) not null,
    status varchar(24) not null,
    session_json text not null,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version integer not null default 0
);

create index ix_physical_count_sessions_tenant_status on physical_count_sessions (tenant_id, status);
create index ix_physical_count_sessions_tenant_location on physical_count_sessions (tenant_id, location_id);
create index ix_physical_count_sessions_tenant_updated on physical_count_sessions (tenant_id, updated_at);
