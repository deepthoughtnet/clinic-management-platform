alter table if exists consultations
    add column if not exists respiratory_rate integer;

alter table if exists doctor_availability
    add column if not exists break_start_time time,
    add column if not exists break_end_time time,
    add column if not exists max_patients_per_slot integer;
