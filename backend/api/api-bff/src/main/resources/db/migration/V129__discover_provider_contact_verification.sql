-- Discover domain owner: provider contact verification for onboarding submission readiness.

create table if not exists discover_provider_contact_verifications (
    provider_id uuid primary key,
    email_normalized varchar(256) not null,
    phone_normalized varchar(32) not null,
    email_otp_hash varchar(255),
    email_otp_expires_at timestamptz,
    email_otp_attempts integer not null default 0,
    email_otp_sent_at timestamptz,
    email_verified_at timestamptz,
    phone_otp_hash varchar(255),
    phone_otp_expires_at timestamptz,
    phone_otp_attempts integer not null default 0,
    phone_otp_sent_at timestamptz,
    phone_verified_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists ix_discover_provider_contact_verifications_email
    on discover_provider_contact_verifications (email_normalized, updated_at desc);

create index if not exists ix_discover_provider_contact_verifications_phone
    on discover_provider_contact_verifications (phone_normalized, updated_at desc);
