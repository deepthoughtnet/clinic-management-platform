alter table if exists doctor_profiles
    add column if not exists consultation_fee numeric(12,2),
    add column if not exists years_of_experience integer,
    add column if not exists age integer;
