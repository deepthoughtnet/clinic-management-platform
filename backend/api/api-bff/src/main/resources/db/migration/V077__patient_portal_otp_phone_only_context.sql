alter table if exists patient_portal_otp_challenges
    alter column tenant_id drop not null;

create index if not exists ix_patient_portal_otp_phone_created
    on patient_portal_otp_challenges (phone_normalized, created_at desc);
