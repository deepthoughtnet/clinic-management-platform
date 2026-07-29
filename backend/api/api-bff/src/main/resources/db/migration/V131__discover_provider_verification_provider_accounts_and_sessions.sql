create table if not exists discover_provider_accounts (
    id uuid primary key,
    normalized_email varchar(256),
    normalized_phone varchar(32),
    email_verified_at timestamptz,
    phone_verified_at timestamptz,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    row_version bigint not null default 0,
    constraint uq_discover_provider_accounts_normalized_email unique (normalized_email),
    constraint uq_discover_provider_accounts_normalized_phone unique (normalized_phone)
);

create index if not exists ix_discover_provider_accounts_email
    on discover_provider_accounts (normalized_email, updated_at desc);

create index if not exists ix_discover_provider_accounts_phone
    on discover_provider_accounts (normalized_phone, updated_at desc);

alter table if exists discover_provider_applications
    add column if not exists provider_account_id uuid;

create index if not exists ix_discover_provider_applications_provider_account
    on discover_provider_applications (provider_account_id);

create table if not exists discover_verification_challenges (
    id uuid primary key,
    purpose varchar(64) not null,
    channel varchar(16) not null,
    normalized_recipient varchar(256) not null,
    code_hash varchar(255) not null,
    provider_application_id uuid,
    provider_account_id uuid,
    expires_at timestamptz not null,
    attempt_count integer not null default 0,
    max_attempts integer not null,
    resend_available_at timestamptz not null,
    consumed_at timestamptz,
    invalidated_at timestamptz,
    delivery_provider varchar(128) not null,
    delivery_reference varchar(256),
    created_by_context varchar(128) not null,
    code_hint varchar(16),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists ix_discover_verification_challenges_lookup
    on discover_verification_challenges (purpose, channel, normalized_recipient, created_at desc);

create index if not exists ix_discover_verification_challenges_application
    on discover_verification_challenges (provider_application_id, created_at desc);

create index if not exists ix_discover_verification_challenges_account
    on discover_verification_challenges (provider_account_id, created_at desc);

create table if not exists discover_provider_sessions (
    id uuid primary key,
    provider_account_id uuid not null,
    session_token_hash varchar(255) not null unique,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    last_seen_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    row_version bigint not null default 0,
    constraint fk_discover_provider_sessions_account foreign key (provider_account_id) references discover_provider_accounts(id)
);

create index if not exists ix_discover_provider_sessions_account
    on discover_provider_sessions (provider_account_id, created_at desc);

alter table if exists discover_provider_applications
    add constraint fk_discover_provider_applications_account foreign key (provider_account_id) references discover_provider_accounts(id);

alter table if exists discover_verification_challenges
    add constraint fk_discover_verification_challenges_application foreign key (provider_application_id) references discover_provider_applications(id);

alter table if exists discover_verification_challenges
    add constraint fk_discover_verification_challenges_account foreign key (provider_account_id) references discover_provider_accounts(id);
