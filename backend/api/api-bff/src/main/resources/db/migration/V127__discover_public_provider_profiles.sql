CREATE TABLE discover_public_provider_profiles (
    provider_id UUID PRIMARY KEY REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    provider_type VARCHAR(32) NOT NULL,
    canonical_slug VARCHAR(256) NOT NULL UNIQUE,
    latest_published_version_number INTEGER NOT NULL,
    latest_published_version_id UUID NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    legal_name VARCHAR(256),
    summary VARCHAR(2000),
    primary_speciality VARCHAR(256),
    specialities VARCHAR(1000),
    sub_specialities VARCHAR(1000),
    services VARCHAR(1000),
    departments VARCHAR(1000),
    facilities VARCHAR(1000),
    languages VARCHAR(1000),
    consultation_modes VARCHAR(512),
    logo_document_id UUID,
    cover_image_document_id UUID,
    doctor_photo_document_id UUID,
    contact_phone VARCHAR(64),
    contact_email VARCHAR(256),
    website VARCHAR(256),
    city VARCHAR(128),
    area VARCHAR(128),
    state VARCHAR(128),
    country VARCHAR(128),
    tagline VARCHAR(256),
    ownership VARCHAR(128),
    hospital_type VARCHAR(128),
    medical_director VARCHAR(256),
    beds INTEGER,
    emergency_available BOOLEAN NOT NULL DEFAULT FALSE,
    doctor_count INTEGER NOT NULL DEFAULT 0,
    service_count INTEGER NOT NULL DEFAULT 0,
    department_count INTEGER NOT NULL DEFAULT 0,
    gallery_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ NOT NULL,
    publication_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_discover_public_provider_profiles_provider_type_city
    ON discover_public_provider_profiles(provider_type, city, area);
CREATE INDEX ix_discover_public_provider_profiles_provider_type_speciality
    ON discover_public_provider_profiles(provider_type, primary_speciality);
CREATE INDEX ix_discover_public_provider_profiles_display_name
    ON discover_public_provider_profiles(display_name);

CREATE TABLE discover_public_provider_profile_versions (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    source_submission_version_number INTEGER NOT NULL,
    status_before VARCHAR(32),
    status_after VARCHAR(32) NOT NULL,
    published_by VARCHAR(64),
    publication_reason VARCHAR(1000),
    snapshot_hash VARCHAR(128) NOT NULL,
    snapshot_json TEXT NOT NULL,
    canonical_slug VARCHAR(256) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_discover_public_provider_profile_versions_provider_version UNIQUE (provider_id, version_number),
    CONSTRAINT uq_discover_public_provider_profile_versions_provider_hash UNIQUE (provider_id, snapshot_hash)
);

CREATE INDEX ix_discover_public_provider_profile_versions_provider_published
    ON discover_public_provider_profile_versions(provider_id, published_at DESC);

CREATE TABLE discover_public_provider_profile_slugs (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    profile_version_id UUID NOT NULL REFERENCES discover_public_provider_profile_versions(id) ON DELETE CASCADE,
    slug VARCHAR(256) NOT NULL UNIQUE,
    version_number INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_discover_public_provider_profile_slugs_provider_active
    ON discover_public_provider_profile_slugs(provider_id, active, updated_at DESC);
