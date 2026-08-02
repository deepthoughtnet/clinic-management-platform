ALTER TABLE public_clinic_platform_links
    ADD COLUMN IF NOT EXISTS evidence_snapshot_json jsonb;

ALTER TABLE public_doctor_practice_platform_links
    ADD COLUMN IF NOT EXISTS evidence_snapshot_json jsonb;

CREATE TABLE IF NOT EXISTS provider_connection_suggestion_rejections (
    id UUID PRIMARY KEY,
    suggestion_key VARCHAR(320) NOT NULL UNIQUE,
    public_profile_type VARCHAR(40) NOT NULL,
    public_reference VARCHAR(160),
    public_practice_reference VARCHAR(160),
    tenant_reference VARCHAR(160),
    platform_clinic_reference VARCHAR(160),
    source_revision BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    metadata_json jsonb NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IF NOT EXISTS ix_provider_connection_suggestion_rejections_profile
    ON provider_connection_suggestion_rejections(public_profile_type, public_reference, public_practice_reference, tenant_reference, platform_clinic_reference);
