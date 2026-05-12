alter table if exists tenants
    add column if not exists module_carepilot boolean not null default false;

-- Transitional backfill: preserve current effective access for tenants that previously
-- relied on tele-calling entitlement for CarePilot route/module checks.
update tenants
set module_carepilot = module_tele_calling
where module_carepilot = false
  and module_tele_calling = true;
