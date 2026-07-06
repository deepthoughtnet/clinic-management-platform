alter table if exists app_users
    add column if not exists employee_code varchar(64),
    add column if not exists mobile varchar(32),
    add column if not exists last_login_at timestamp with time zone;

alter table if exists doctor_profiles
    add column if not exists specializations_json text,
    add column if not exists opd_fee numeric(12,2),
    add column if not exists follow_up_fee numeric(12,2),
    add column if not exists emergency_fee numeric(12,2),
    add column if not exists photo_storage_key varchar(512),
    add column if not exists photo_content_type varchar(128),
    add column if not exists photo_size_bytes bigint,
    add column if not exists photo_original_filename varchar(256);

create unique index if not exists uq_doctor_profiles_tenant_registration_active
    on doctor_profiles (tenant_id, lower(registration_number))
    where registration_number is not null and active;
