alter table if exists vaccines
    add column if not exists manufacturer varchar(256) null,
    add column if not exists brand_name varchar(256) null,
    add column if not exists vaccine_group varchar(128) null,
    add column if not exists dose_number integer null,
    add column if not exists route varchar(32) null,
    add column if not exists administration_site varchar(128) null,
    add column if not exists storage_temperature varchar(128) null,
    add column if not exists ndc_barcode varchar(128) null,
    add column if not exists schedule_type varchar(32) null,
    add column if not exists min_age_days integer null,
    add column if not exists recommended_age_days integer null,
    add column if not exists max_age_days integer null,
    add column if not exists booster_gap_days integer null,
    add column if not exists booster_rules text null,
    add column if not exists is_recurring boolean not null default false,
    add column if not exists recurrence_days integer null;

update vaccines
set is_recurring = coalesce(is_recurring, false);

create index if not exists ix_vaccines_tenant_schedule on vaccines (tenant_id, schedule_type, active);
create index if not exists ix_vaccines_tenant_group_dose on vaccines (tenant_id, vaccine_group, dose_number, schedule_type);
