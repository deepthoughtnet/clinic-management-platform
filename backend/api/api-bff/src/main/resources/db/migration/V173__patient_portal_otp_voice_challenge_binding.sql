alter table if exists patient_portal_otp_challenges
    add column if not exists identified_patient_id uuid,
    add column if not exists provider_call_id varchar(200),
    add column if not exists interaction_id uuid,
    add column if not exists channel varchar(24);

create index if not exists ix_patient_portal_otp_voice_binding
    on patient_portal_otp_challenges (tenant_id, identified_patient_id, provider_call_id, channel, created_at desc);
