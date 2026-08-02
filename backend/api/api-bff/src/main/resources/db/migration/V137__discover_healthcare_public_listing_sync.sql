ALTER TABLE discover_public_provider_profiles
    DROP CONSTRAINT IF EXISTS discover_public_provider_profiles_provider_id_fkey;

ALTER TABLE discover_public_provider_profile_versions
    DROP CONSTRAINT IF EXISTS discover_public_provider_profile_versions_provider_id_fkey;

ALTER TABLE discover_public_provider_profile_slugs
    DROP CONSTRAINT IF EXISTS discover_public_provider_profile_slugs_provider_id_fkey;

ALTER TABLE discover_provider_documents
    DROP CONSTRAINT IF EXISTS discover_provider_documents_provider_id_fkey;

ALTER TABLE discover_public_provider_profiles
    ADD COLUMN IF NOT EXISTS source_system VARCHAR(64),
    ADD COLUMN IF NOT EXISTS booking_mode VARCHAR(32);

UPDATE discover_public_provider_profiles
SET source_system = COALESCE(NULLIF(source_system, ''), 'DISCOVER_ONBOARDING_APPLICATION'),
    booking_mode = COALESCE(NULLIF(booking_mode, ''), 'ONLINE_BOOKING');

ALTER TABLE discover_public_provider_profiles
    ALTER COLUMN source_system SET NOT NULL,
    ALTER COLUMN booking_mode SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_discover_public_provider_profiles_source_system
    ON discover_public_provider_profiles(source_system);
