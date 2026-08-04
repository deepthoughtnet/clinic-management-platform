CREATE TABLE discover_public_profile_drafts (
    id UUID PRIMARY KEY,
    draft_reference VARCHAR(120) NOT NULL UNIQUE,
    public_profile_reference VARCHAR(160) NOT NULL UNIQUE,
    public_profile_type VARCHAR(32) NOT NULL,
    provider_account_id UUID NOT NULL,
    content_status VARCHAR(32) NOT NULL,
    readiness_status VARCHAR(32) NOT NULL,
    completeness_percentage INTEGER NOT NULL DEFAULT 0,
    ownership_status VARCHAR(32) NOT NULL,
    tenant_consent_status VARCHAR(32) NOT NULL,
    public_profile_status VARCHAR(32) NOT NULL,
    current_version INTEGER NOT NULL,
    source_system VARCHAR(64),
    source_reference VARCHAR(160),
    source_revision BIGINT NOT NULL DEFAULT 0,
    source_updated_at TIMESTAMPTZ,
    display_name VARCHAR(256),
    canonical_slug VARCHAR(256),
    city VARCHAR(128),
    area VARCHAR(128),
    state VARCHAR(128),
    country VARCHAR(128),
    public_phone VARCHAR(64),
    public_email VARCHAR(256),
    website VARCHAR(256),
    whatsapp_number VARCHAR(64),
    registration_number VARCHAR(128),
    established_year INTEGER,
    last_saved_at TIMESTAMPTZ,
    created_by_provider_account_id UUID,
    updated_by_provider_account_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    optimistic_lock_version BIGINT NOT NULL DEFAULT 0,
    public_path VARCHAR(256),
    content_json TEXT NOT NULL,
    source_attribution_json TEXT NOT NULL,
    readiness_json TEXT NOT NULL
);

CREATE INDEX ix_discover_public_profile_drafts_provider_account
    ON discover_public_profile_drafts(provider_account_id, updated_at DESC);

CREATE INDEX ix_discover_public_profile_drafts_status
    ON discover_public_profile_drafts(ownership_status, readiness_status, content_status);

CREATE TABLE discover_public_profile_draft_versions (
    id UUID PRIMARY KEY,
    draft_reference VARCHAR(120) NOT NULL,
    public_profile_reference VARCHAR(160) NOT NULL,
    version_number INTEGER NOT NULL,
    change_summary VARCHAR(1000),
    content_json TEXT NOT NULL,
    readiness_json TEXT NOT NULL,
    source_attribution_json TEXT NOT NULL,
    created_by_provider_account_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_discover_public_profile_draft_versions_draft_version UNIQUE (draft_reference, version_number)
);

CREATE INDEX ix_discover_public_profile_draft_versions_draft
    ON discover_public_profile_draft_versions(draft_reference, version_number DESC);
