alter table if exists app_users
    add column if not exists username varchar(128),
    add column if not exists department varchar(128);

create index if not exists ix_app_users_tenant_username
    on app_users (tenant_id, username);

create index if not exists ix_app_users_tenant_employee_code
    on app_users (tenant_id, employee_code);

create unique index if not exists uq_app_users_tenant_username
    on app_users (tenant_id, lower(username))
    where username is not null and btrim(username) <> '';

create unique index if not exists uq_app_users_tenant_employee_code
    on app_users (tenant_id, lower(employee_code))
    where employee_code is not null and btrim(employee_code) <> '';

create table if not exists tenant_onboarding_statuses (
    id uuid primary key,
    tenant_id uuid not null,
    completed boolean not null default false,
    skipped boolean not null default false,
    completed_at timestamp with time zone,
    skipped_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_tenant_onboarding_statuses_tenant unique (tenant_id)
);

create index if not exists ix_tenant_onboarding_statuses_tenant
    on tenant_onboarding_statuses (tenant_id);

insert into tenant_onboarding_statuses (id, tenant_id, completed, skipped, completed_at, skipped_at, created_at, updated_at)
select gen_random_uuid(), t.id, true, false, now(), null, now(), now()
from tenants t
where not exists (
    select 1 from tenant_onboarding_statuses tos where tos.tenant_id = t.id
);
