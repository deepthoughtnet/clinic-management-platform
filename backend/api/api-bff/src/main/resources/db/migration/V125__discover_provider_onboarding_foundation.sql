-- Discover domain owner: provider onboarding and verification foundation.
CREATE TABLE discover_provider_applications (
    id UUID PRIMARY KEY,
    reference_number VARCHAR(32) NOT NULL UNIQUE,
    provider_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(256) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    contact_verified BOOLEAN NOT NULL DEFAULT FALSE,
    terms_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    display_name VARCHAR(256),
    legal_name VARCHAR(256),
    organisation_type VARCHAR(128),
    registration_number VARCHAR(128),
    gst_number VARCHAR(64),
    website VARCHAR(256),
    gender VARCHAR(32),
    date_of_birth DATE,
    languages VARCHAR(512),
    biography VARCHAR(2000),
    medical_council VARCHAR(128),
    qualification VARCHAR(256),
    years_of_experience INTEGER,
    specialities VARCHAR(512),
    sub_specialities VARCHAR(512),
    consultation_fee NUMERIC(12,2),
    online_consultation BOOLEAN NOT NULL DEFAULT FALSE,
    appointment_duration_minutes INTEGER,
    ownership VARCHAR(128),
    hospital_type VARCHAR(128),
    beds INTEGER,
    emergency_available BOOLEAN NOT NULL DEFAULT FALSE,
    medical_director VARCHAR(256),
    departments VARCHAR(512),
    facilities VARCHAR(512),
    accreditations VARCHAR(512),
    logo_document_id UUID,
    cover_image_document_id UUID,
    doctor_photo_document_id UUID,
    primary_color VARCHAR(24),
    tagline VARCHAR(256),
    completion_percent INTEGER NOT NULL DEFAULT 0,
    current_step VARCHAR(64) NOT NULL DEFAULT 'ACCOUNT',
    last_saved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_discover_provider_type CHECK (provider_type IN ('INDIVIDUAL_DOCTOR','CLINIC','HOSPITAL')),
    CONSTRAINT ck_discover_provider_status CHECK (status IN ('DRAFT','CONTACT_VERIFIED','PROFILE_INCOMPLETE','READY_FOR_REVIEW','SUBMITTED','UNDER_REVIEW','CHANGES_REQUESTED','APPROVED','PUBLISHED','SUSPENDED','ARCHIVED'))
);

CREATE INDEX ix_discover_provider_applications_status ON discover_provider_applications(status);
CREATE INDEX ix_discover_provider_applications_email ON discover_provider_applications(lower(email));

CREATE TABLE discover_provider_locations (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    label VARCHAR(128),
    address VARCHAR(512) NOT NULL,
    city VARCHAR(128) NOT NULL,
    state VARCHAR(128) NOT NULL,
    country VARCHAR(128) NOT NULL,
    pin_code VARCHAR(32) NOT NULL,
    working_hours VARCHAR(512),
    parking_available BOOLEAN NOT NULL DEFAULT FALSE,
    accessibility_available BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_discover_provider_locations_provider ON discover_provider_locations(provider_id);

CREATE TABLE discover_provider_services (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    service_type VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_discover_provider_service_type CHECK (service_type IN ('CONSULTATIONS','VACCINATION','LAB','RADIOLOGY','TELECONSULTATION','PHARMACY','HEALTH_CHECKUPS','PROCEDURES'))
);

CREATE INDEX ix_discover_provider_services_provider ON discover_provider_services(provider_id);

CREATE TABLE discover_provider_documents (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    document_type VARCHAR(64) NOT NULL,
    original_filename VARCHAR(256) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    virus_scan_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_discover_provider_document_type CHECK (document_type IN ('LOGO','COVER_IMAGE','DOCTOR_PHOTO','GALLERY_IMAGE','REGISTRATION_CERTIFICATE','ACCREDITATION','IDENTITY_PROOF','OTHER'))
);

CREATE INDEX ix_discover_provider_documents_provider ON discover_provider_documents(provider_id);

CREATE TABLE discover_provider_submissions (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_discover_provider_submission_version UNIQUE(provider_id, version_number)
);

CREATE TABLE discover_provider_status_history (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_discover_provider_status_history_provider ON discover_provider_status_history(provider_id, created_at);
