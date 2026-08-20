create unique index if not exists uq_clinic_profiles_slug_lower
    on clinic_profiles (lower(slug))
    where slug is not null and btrim(slug) <> '';
