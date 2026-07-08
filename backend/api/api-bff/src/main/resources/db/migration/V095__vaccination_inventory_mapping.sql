alter table if exists vaccines
    add column if not exists inventory_item_id uuid null;

alter table if exists vaccines
    add column if not exists inventory_item_code varchar(128) null;

alter table if exists vaccines
    add column if not exists stock_tracking_enabled boolean not null default false;

alter table if exists patient_vaccinations
    add column if not exists inventory_item_id uuid null;

alter table if exists patient_vaccinations
    add column if not exists inventory_item_code varchar(128) null;
