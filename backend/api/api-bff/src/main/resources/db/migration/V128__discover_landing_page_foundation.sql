CREATE TABLE discover_landing_page_templates (
    template_key VARCHAR(64) PRIMARY KEY,
    template_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    template_version INTEGER NOT NULL,
    description VARCHAR(1000),
    supported_sections_json TEXT NOT NULL,
    default_sections_json TEXT NOT NULL,
    default_theme_json TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_discover_landing_page_templates_provider_type
    ON discover_landing_page_templates(provider_type, active, sort_order);

CREATE TABLE discover_landing_pages (
    provider_id UUID PRIMARY KEY REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    provider_type VARCHAR(32) NOT NULL,
    canonical_slug VARCHAR(256) NOT NULL UNIQUE,
    template_key VARCHAR(64) NOT NULL REFERENCES discover_landing_page_templates(template_key),
    draft_snapshot_json TEXT NOT NULL,
    published_snapshot_json TEXT,
    published_version_id UUID,
    published_version_number INTEGER,
    draft_updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_discover_landing_pages_provider_type
    ON discover_landing_pages(provider_type, published_at DESC);

CREATE TABLE discover_landing_page_versions (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    template_key VARCHAR(64) NOT NULL REFERENCES discover_landing_page_templates(template_key),
    template_version INTEGER NOT NULL,
    version_kind VARCHAR(32) NOT NULL,
    change_summary VARCHAR(1000) NOT NULL,
    snapshot_hash VARCHAR(128) NOT NULL,
    snapshot_json TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_discover_landing_page_versions_provider_version UNIQUE (provider_id, version_number),
    CONSTRAINT uq_discover_landing_page_versions_provider_hash UNIQUE (provider_id, snapshot_hash)
);

CREATE INDEX ix_discover_landing_page_versions_provider_published
    ON discover_landing_page_versions(provider_id, published_at DESC);

INSERT INTO discover_landing_page_templates (
    template_key,
    template_name,
    provider_type,
    template_version,
    description,
    supported_sections_json,
    default_sections_json,
    default_theme_json,
    sort_order,
    active
) VALUES
    (
        'SOLO_DOCTOR_V1',
        'Solo Doctor',
        'INDIVIDUAL_DOCTOR',
        1,
        'Single-provider landing page optimized for one specialist doctor.',
        $$["HERO","ABOUT","SERVICES","WORKING_HOURS","GALLERY","FAQ","CONTACT","CTA"]$$,
        $$[
            {"key":"HERO","enabled":true,"displayOrder":0,"title":"Welcome","description":"A focused introduction for the doctor.","visibilityRule":"PUBLIC","content":{}},
            {"key":"ABOUT","enabled":true,"displayOrder":1,"title":"About","description":"Profile summary, qualifications, and approach to care.","visibilityRule":"PUBLIC","content":{}},
            {"key":"SERVICES","enabled":true,"displayOrder":2,"title":"Services","description":"Consultations and care offered.","visibilityRule":"PUBLIC","content":{}},
            {"key":"WORKING_HOURS","enabled":true,"displayOrder":3,"title":"Working Hours","description":"Clinic schedule and visit availability.","visibilityRule":"PUBLIC","content":{}},
            {"key":"GALLERY","enabled":true,"displayOrder":4,"title":"Gallery","description":"Practice photos and public media.","visibilityRule":"PUBLIC","content":{}},
            {"key":"FAQ","enabled":true,"displayOrder":5,"title":"Frequently Asked Questions","description":"Common patient questions answered in advance.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CONTACT","enabled":true,"displayOrder":6,"title":"Contact & Map","description":"Phone, address and map information.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CTA","enabled":true,"displayOrder":7,"title":"Book Appointment","description":"Prompt for booking or enquiry.","visibilityRule":"PUBLIC","content":{}}
        ]$$,
        $${
            "primaryColor":"#0F8B8D",
            "accentColor":"#1E88E5",
            "typographyPreset":"clean",
            "buttonStyle":"solid",
            "borderRadiusPreset":"medium"
        }$$,
        1,
        TRUE
    ),
    (
        'FAMILY_CLINIC_V1',
        'Family Clinic',
        'CLINIC',
        1,
        'Clinic template with doctors, services, hours, and trust content.',
        $$["HERO","ABOUT","SERVICES","DOCTORS","WORKING_HOURS","GALLERY","FAQ","CONTACT","CTA"]$$,
        $$[
            {"key":"HERO","enabled":true,"displayOrder":0,"title":"Welcome","description":"A warm and professional clinic introduction.","visibilityRule":"PUBLIC","content":{}},
            {"key":"ABOUT","enabled":true,"displayOrder":1,"title":"About","description":"Clinic story and care philosophy.","visibilityRule":"PUBLIC","content":{}},
            {"key":"SERVICES","enabled":true,"displayOrder":2,"title":"Services","description":"Primary services offered by the clinic.","visibilityRule":"PUBLIC","content":{}},
            {"key":"DOCTORS","enabled":true,"displayOrder":3,"title":"Our Doctors","description":"Doctors associated with the clinic.","visibilityRule":"PUBLIC","content":{}},
            {"key":"WORKING_HOURS","enabled":true,"displayOrder":4,"title":"Working Hours","description":"Daily opening and closing times.","visibilityRule":"PUBLIC","content":{}},
            {"key":"GALLERY","enabled":true,"displayOrder":5,"title":"Gallery","description":"Clinic imagery and public photos.","visibilityRule":"PUBLIC","content":{}},
            {"key":"FAQ","enabled":true,"displayOrder":6,"title":"Frequently Asked Questions","description":"Helpful patient guidance.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CONTACT","enabled":true,"displayOrder":7,"title":"Contact & Map","description":"Contact details and directions.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CTA","enabled":true,"displayOrder":8,"title":"Book Appointment","description":"Appointment booking call to action.","visibilityRule":"PUBLIC","content":{}}
        ]$$,
        $${
            "primaryColor":"#0F8B8D",
            "accentColor":"#0B5FA7",
            "typographyPreset":"warm",
            "buttonStyle":"solid",
            "borderRadiusPreset":"large"
        }$$,
        2,
        TRUE
    ),
    (
        'MULTI_SPECIALITY_CLINIC_V1',
        'Multi-speciality Clinic',
        'CLINIC',
        1,
        'Clinic template for larger practices with departments and multiple care teams.',
        $$["HERO","ABOUT","SERVICES","DOCTORS","DEPARTMENTS","FACILITIES","WORKING_HOURS","GALLERY","FAQ","CONTACT","CTA"]$$,
        $$[
            {"key":"HERO","enabled":true,"displayOrder":0,"title":"Welcome","description":"Broad practice introduction with clear care categories.","visibilityRule":"PUBLIC","content":{}},
            {"key":"ABOUT","enabled":true,"displayOrder":1,"title":"About","description":"Clinic overview and care strengths.","visibilityRule":"PUBLIC","content":{}},
            {"key":"SERVICES","enabled":true,"displayOrder":2,"title":"Services","description":"Clinical and diagnostic services available.","visibilityRule":"PUBLIC","content":{}},
            {"key":"DOCTORS","enabled":true,"displayOrder":3,"title":"Our Doctors","description":"Doctors and consultants on the team.","visibilityRule":"PUBLIC","content":{}},
            {"key":"DEPARTMENTS","enabled":true,"displayOrder":4,"title":"Departments","description":"Speciality or department coverage.","visibilityRule":"PUBLIC","content":{}},
            {"key":"FACILITIES","enabled":true,"displayOrder":5,"title":"Facilities","description":"Amenities and support services.","visibilityRule":"PUBLIC","content":{}},
            {"key":"WORKING_HOURS","enabled":true,"displayOrder":6,"title":"Working Hours","description":"Practice availability.","visibilityRule":"PUBLIC","content":{}},
            {"key":"GALLERY","enabled":true,"displayOrder":7,"title":"Gallery","description":"Public images and facility views.","visibilityRule":"PUBLIC","content":{}},
            {"key":"FAQ","enabled":true,"displayOrder":8,"title":"Frequently Asked Questions","description":"Answers to common queries.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CONTACT","enabled":true,"displayOrder":9,"title":"Contact & Map","description":"Directions and contact information.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CTA","enabled":true,"displayOrder":10,"title":"Book Appointment","description":"Primary booking call to action.","visibilityRule":"PUBLIC","content":{}}
        ]$$,
        $${
            "primaryColor":"#116A7B",
            "accentColor":"#D97706",
            "typographyPreset":"balanced",
            "buttonStyle":"rounded",
            "borderRadiusPreset":"large"
        }$$,
        3,
        TRUE
    ),
    (
        'HOSPITAL_V1',
        'Hospital',
        'HOSPITAL',
        1,
        'Hospital template with departments, facilities, and broad public information.',
        $$["HERO","ABOUT","DEPARTMENTS","FACILITIES","SERVICES","DOCTORS","CONSULTATION_MODES","WORKING_HOURS","GALLERY","INSURANCE","AWARDS","FAQ","CONTACT","CTA"]$$,
        $$[
            {"key":"HERO","enabled":true,"displayOrder":0,"title":"Welcome","description":"Hospital introduction and trust-led headline.","visibilityRule":"PUBLIC","content":{}},
            {"key":"ABOUT","enabled":true,"displayOrder":1,"title":"About","description":"Overview of services and care mission.","visibilityRule":"PUBLIC","content":{}},
            {"key":"DEPARTMENTS","enabled":true,"displayOrder":2,"title":"Departments","description":"Departmental coverage and specialities.","visibilityRule":"PUBLIC","content":{}},
            {"key":"FACILITIES","enabled":true,"displayOrder":3,"title":"Facilities","description":"Infrastructure and patient support services.","visibilityRule":"PUBLIC","content":{}},
            {"key":"SERVICES","enabled":true,"displayOrder":4,"title":"Services","description":"Clinical services and support offerings.","visibilityRule":"PUBLIC","content":{}},
            {"key":"DOCTORS","enabled":true,"displayOrder":5,"title":"Our Doctors","description":"Doctors and consultants available to patients.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CONSULTATION_MODES","enabled":true,"displayOrder":6,"title":"Consultation Modes","description":"In-person and virtual consultation options.","visibilityRule":"PUBLIC","content":{}},
            {"key":"WORKING_HOURS","enabled":true,"displayOrder":7,"title":"Working Hours","description":"Hospital availability and visiting hours.","visibilityRule":"PUBLIC","content":{}},
            {"key":"GALLERY","enabled":true,"displayOrder":8,"title":"Gallery","description":"Public-facing images and facility views.","visibilityRule":"PUBLIC","content":{}},
            {"key":"INSURANCE","enabled":true,"displayOrder":9,"title":"Insurance Accepted","description":"Supported insurance and billing options.","visibilityRule":"PUBLIC","content":{}},
            {"key":"AWARDS","enabled":true,"displayOrder":10,"title":"Awards & Certifications","description":"Accreditations and recognitions.","visibilityRule":"PUBLIC","content":{}},
            {"key":"FAQ","enabled":true,"displayOrder":11,"title":"Frequently Asked Questions","description":"Helpful patient guidance.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CONTACT","enabled":true,"displayOrder":12,"title":"Contact & Map","description":"Contact details and location guidance.","visibilityRule":"PUBLIC","content":{}},
            {"key":"CTA","enabled":true,"displayOrder":13,"title":"Book Appointment","description":"Primary booking prompt.","visibilityRule":"PUBLIC","content":{}}
        ]$$,
        $${
            "primaryColor":"#084C61",
            "accentColor":"#F97316",
            "typographyPreset":"institutional",
            "buttonStyle":"solid",
            "borderRadiusPreset":"medium"
        }$$,
        4,
        TRUE
    );
