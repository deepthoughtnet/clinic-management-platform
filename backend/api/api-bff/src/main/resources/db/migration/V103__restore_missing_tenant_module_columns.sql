alter table if exists tenants
    add column if not exists module_carepilot boolean not null default false;

alter table if exists tenants
    add column if not exists public_listing_enabled boolean not null default false;

update tenants
set module_carepilot = module_tele_calling
where module_carepilot = false
  and module_tele_calling = true;
