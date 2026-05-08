drop index if exists uq_patients_active_mobile;

alter table appointments
    add column if not exists priority varchar(24) not null default 'NORMAL';
