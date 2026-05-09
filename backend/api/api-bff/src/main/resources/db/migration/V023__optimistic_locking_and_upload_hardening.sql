alter table if exists appointments
    add column if not exists version integer not null default 0;

alter table if exists consultations
    add column if not exists version integer not null default 0;

alter table if exists bills
    add column if not exists version integer not null default 0;

alter table if exists prescriptions
    add column if not exists version integer not null default 0;
