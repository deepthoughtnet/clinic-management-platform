-- Backward-compatible SaaS plan/subscription/module control.
-- NOTE: tenant_plans already exists in V001 with varchar id used by tenants.plan_id.
-- We extend it rather than replacing schema to preserve existing tenant flow.

alter table if exists tenant_plans
    add column if not exists code varchar(32);

alter table if exists tenant_plans
    add column if not exists description varchar(512);

alter table if exists tenant_plans
    add column if not exists max_users integer;

alter table if exists tenant_plans
    add column if not exists max_doctors integer;

alter table if exists tenant_plans
    add column if not exists max_patients integer;

alter table if exists tenant_plans
    add column if not exists created_at timestamp with time zone;

create unique index if not exists uq_tenant_plans_code on tenant_plans(code);

update tenant_plans
set code = upper(id)
where code is null;

update tenant_plans
set created_at = now()
where created_at is null;

create table if not exists tenant_subscriptions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id) on delete cascade,
    plan_id varchar(32) not null references tenant_plans(id),
    start_date date not null,
    end_date date,
    status varchar(32) not null,
    trial boolean not null default false,
    created_at timestamp with time zone not null default now()
);

create index if not exists ix_tenant_subscriptions_tenant on tenant_subscriptions(tenant_id);
create index if not exists ix_tenant_subscriptions_tenant_status on tenant_subscriptions(tenant_id, status);
create index if not exists ix_tenant_subscriptions_tenant_dates on tenant_subscriptions(tenant_id, start_date, end_date);

create table if not exists tenant_modules (
    id uuid primary key,
    tenant_id uuid not null references tenants(id) on delete cascade,
    module_code varchar(64) not null,
    enabled boolean not null default true,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_tenant_modules_tenant_code unique (tenant_id, module_code)
);

create index if not exists ix_tenant_modules_tenant on tenant_modules(tenant_id);
create index if not exists ix_tenant_modules_tenant_enabled on tenant_modules(tenant_id, enabled);

-- Default plan seeds requested by product.
insert into tenant_plans (id, code, name, description, max_users, max_doctors, max_patients, created_at, features)
values
    ('FREE', 'FREE', 'Free', 'Free starter plan', 5, 2, 200, now(), '{}'),
    ('PRO', 'PRO', 'Pro', 'Professional clinic plan', 100, 30, 20000, now(), '{}')
on conflict (id) do nothing;
