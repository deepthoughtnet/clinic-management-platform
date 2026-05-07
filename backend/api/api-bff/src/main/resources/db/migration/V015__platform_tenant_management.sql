-- Platform tenant management hardening for SaaS control plane.
-- V013 is already used by audit/outbox migration in this repository,
-- so this migration applies additional platform control-plane constraints.

alter table if exists tenants
    alter column status set default 'ACTIVE';

alter table if exists tenants
    alter column status set not null;

create index if not exists ix_tenants_status on tenants(status);
create index if not exists ix_tenants_plan_id on tenants(plan_id);

alter table if exists tenant_subscriptions
    alter column status set not null;

create index if not exists ix_tenant_subscriptions_tenant_created_at on tenant_subscriptions(tenant_id, created_at desc);

create unique index if not exists uq_clinic_profiles_email_lower
    on clinic_profiles (lower(email))
    where email is not null;
