CREATE TABLE discover_public_profile_submissions (
    id UUID PRIMARY KEY,
    submission_reference VARCHAR(120) NOT NULL UNIQUE,
    public_profile_reference VARCHAR(160) NOT NULL,
    public_profile_type VARCHAR(32) NOT NULL,
    draft_reference VARCHAR(120) NOT NULL,
    submitted_draft_version INTEGER NOT NULL,
    moderation_status VARCHAR(32) NOT NULL,
    publication_status_snapshot VARCHAR(32) NOT NULL,
    tenant_consent_status_snapshot VARCHAR(32) NOT NULL,
    ownership_snapshot_json TEXT NOT NULL,
    readiness_snapshot_json TEXT NOT NULL,
    content_snapshot_json TEXT NOT NULL,
    source_attribution_snapshot_json TEXT NOT NULL,
    media_snapshot_json TEXT NOT NULL DEFAULT '{}'::text,
    submitted_by_provider_account_id UUID,
    submitted_at TIMESTAMPTZ NOT NULL,
    assigned_reviewer_id UUID,
    assigned_at TIMESTAMPTZ,
    decision_by_id UUID,
    decision_at TIMESTAMPTZ,
    decision_reason VARCHAR(1000),
    moderation_revision BIGINT NOT NULL DEFAULT 0,
    current_flag BOOLEAN NOT NULL DEFAULT TRUE,
    approved_version_number INTEGER,
    published_at TIMESTAMPTZ,
    unpublished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    optimistic_lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_discover_public_profile_submissions_current
    ON discover_public_profile_submissions(public_profile_reference)
    WHERE current_flag;

CREATE INDEX ix_discover_public_profile_submissions_profile_status
    ON discover_public_profile_submissions(public_profile_reference, moderation_status, submitted_at DESC);

CREATE TABLE discover_public_profile_review_findings (
    id UUID PRIMARY KEY,
    finding_reference VARCHAR(120) NOT NULL UNIQUE,
    submission_reference VARCHAR(120) NOT NULL,
    section VARCHAR(64) NOT NULL,
    field_key VARCHAR(128),
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_note VARCHAR(1000),
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    provider_resolution_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    optimistic_lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_discover_public_profile_review_findings_submission
    ON discover_public_profile_review_findings(submission_reference, created_at DESC);

CREATE TABLE discover_public_profile_publications (
    id UUID PRIMARY KEY,
    publication_reference VARCHAR(120) NOT NULL UNIQUE,
    public_profile_reference VARCHAR(160) NOT NULL,
    approved_submission_reference VARCHAR(120) NOT NULL,
    published_version INTEGER NOT NULL,
    publication_status VARCHAR(32) NOT NULL,
    slug VARCHAR(256) NOT NULL,
    public_path VARCHAR(256) NOT NULL,
    reason VARCHAR(1000),
    published_at TIMESTAMPTZ NOT NULL,
    unpublished_at TIMESTAMPTZ,
    current_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    optimistic_lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_discover_public_profile_publications_current
    ON discover_public_profile_publications(public_profile_reference)
    WHERE current_flag;

CREATE INDEX ix_discover_public_profile_publications_profile_status
    ON discover_public_profile_publications(public_profile_reference, publication_status, published_at DESC);
