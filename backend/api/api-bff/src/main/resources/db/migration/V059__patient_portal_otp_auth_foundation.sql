alter table if exists app_users
    add column if not exists patient_id uuid;

create index if not exists ix_app_users_patient
    on app_users (tenant_id, patient_id);

create table if not exists patient_portal_otp_challenges (
    id uuid primary key,
    tenant_id uuid not null,
    phone_normalized varchar(32) not null,
    otp_hash varchar(255) not null,
    attempts integer not null default 0,
    expires_at timestamp with time zone not null,
    verified_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists ix_patient_portal_otp_tenant_phone_created
    on patient_portal_otp_challenges (tenant_id, phone_normalized, created_at desc);
