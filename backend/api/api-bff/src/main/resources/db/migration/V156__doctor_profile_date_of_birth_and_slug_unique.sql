alter table if exists doctor_profiles
    add column if not exists date_of_birth date;

create unique index if not exists uq_doctor_profiles_slug_lower
    on doctor_profiles (lower(slug))
    where slug is not null and btrim(slug) <> '';
