create table if not exists discover_provider_claim_intents (
    id uuid primary key,
    connection_reference varchar(120) not null,
    public_profile_type varchar(32) not null,
    public_profile_reference varchar(160) not null,
    tenant_reference varchar(160) not null,
    provider_account_id uuid,
    issuer_app_user_id uuid,
    source_revision bigint not null default 0,
    status varchar(32) not null,
    expires_at timestamptz not null,
    opened_at timestamptz,
    provider_authenticated_at timestamptz,
    claim_submitted_at timestamptz,
    consumed_at timestamptz,
    revoked_at timestamptz,
    rejected_at timestamptz,
    reason varchar(512),
    evidence_snapshot_json jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    row_version bigint not null default 0,
    constraint uq_discover_provider_claim_intents_connection_reference unique (connection_reference)
);

create index if not exists ix_discover_provider_claim_intents_public_profile on discover_provider_claim_intents (public_profile_reference, public_profile_type);
create index if not exists ix_discover_provider_claim_intents_tenant on discover_provider_claim_intents (tenant_reference);
create index if not exists ix_discover_provider_claim_intents_provider_account on discover_provider_claim_intents (provider_account_id);

create table if not exists discover_public_profile_ownerships (
    id uuid primary key,
    public_profile_reference varchar(160) not null,
    public_profile_type varchar(32) not null,
    provider_account_id uuid not null,
    ownership_status varchar(32) not null,
    ownership_method varchar(64) not null,
    tenant_reference varchar(160),
    source_revision bigint not null default 0,
    verified_at timestamptz,
    revoked_at timestamptz,
    rejection_reason varchar(1000),
    transfer_target_provider_account_id uuid,
    active boolean not null default false,
    reason varchar(1000),
    evidence_snapshot_json jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    row_version bigint not null default 0
);

create unique index if not exists uq_discover_public_profile_ownerships_active_owner
    on discover_public_profile_ownerships (public_profile_reference)
    where active;

create index if not exists ix_discover_public_profile_ownerships_provider_account on discover_public_profile_ownerships (provider_account_id);
create index if not exists ix_discover_public_profile_ownerships_profile on discover_public_profile_ownerships (public_profile_reference, public_profile_type);

create table if not exists discover_public_profile_memberships (
    id uuid primary key,
    public_profile_reference varchar(160) not null,
    provider_account_id uuid not null,
    membership_role varchar(32) not null,
    membership_status varchar(32) not null,
    source_revision bigint not null default 0,
    reason varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    row_version bigint not null default 0,
    constraint uq_discover_public_profile_memberships_role unique (public_profile_reference, provider_account_id, membership_role)
);

create index if not exists ix_discover_public_profile_memberships_profile on discover_public_profile_memberships (public_profile_reference);
create index if not exists ix_discover_public_profile_memberships_provider_account on discover_public_profile_memberships (provider_account_id);

create table if not exists discover_public_profile_disputes (
    id uuid primary key,
    public_profile_reference varchar(160) not null,
    public_profile_type varchar(32) not null,
    ownership_id uuid,
    claim_intent_reference varchar(120),
    dispute_status varchar(32) not null,
    reason varchar(1000),
    resolution_reason varchar(1000),
    opened_by_app_user_id uuid,
    resolved_by_app_user_id uuid,
    opened_at timestamptz not null,
    resolved_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    row_version bigint not null default 0
);

create index if not exists ix_discover_public_profile_disputes_profile on discover_public_profile_disputes (public_profile_reference, public_profile_type);
create index if not exists ix_discover_public_profile_disputes_status on discover_public_profile_disputes (dispute_status);
