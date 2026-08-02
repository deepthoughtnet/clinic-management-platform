ALTER TABLE discover_public_provider_profiles
    ADD COLUMN IF NOT EXISTS source_entity_reference VARCHAR(160),
    ADD COLUMN IF NOT EXISTS source_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS source_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS projected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS connection_revision BIGINT NOT NULL DEFAULT 0;

UPDATE discover_public_provider_profiles
SET source_entity_reference = COALESCE(NULLIF(source_entity_reference, ''), provider_id::text),
    source_revision = COALESCE(source_revision, 0),
    source_updated_at = COALESCE(source_updated_at, published_at),
    projected_at = COALESCE(projected_at, published_at),
    connection_revision = COALESCE(connection_revision, 0)
WHERE source_entity_reference IS NULL
   OR source_updated_at IS NULL
   OR projected_at IS NULL;

ALTER TABLE discover_public_provider_profiles
    ALTER COLUMN source_entity_reference SET NOT NULL,
    ALTER COLUMN source_revision SET NOT NULL,
    ALTER COLUMN projected_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_discover_public_provider_profiles_source_lookup
    ON discover_public_provider_profiles(source_system, source_entity_reference);

