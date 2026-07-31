ALTER TABLE discover_reference_options
    DROP CONSTRAINT IF EXISTS ck_discover_reference_options_category;

ALTER TABLE discover_reference_options
    ADD CONSTRAINT ck_discover_reference_options_category
        CHECK (category IN ('SPECIALITY','SERVICE','FACILITY','OWNERSHIP','ORGANISATION_TYPE','LANGUAGE','COUNTRY','STATE','MEDICAL_COUNCIL'));

INSERT INTO discover_reference_options (id, category, code, display_name, provider_types, display_order, active) VALUES
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a101', 'OWNERSHIP', 'PRIVATE', 'Private', 'INDIVIDUAL_DOCTOR,CLINIC,HOSPITAL', 1, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a102', 'OWNERSHIP', 'GOVERNMENT', 'Government', 'CLINIC,HOSPITAL', 2, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a103', 'OWNERSHIP', 'TRUST', 'Trust', 'CLINIC,HOSPITAL', 3, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a104', 'OWNERSHIP', 'NGO', 'NGO', 'CLINIC,HOSPITAL', 4, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a105', 'OWNERSHIP', 'CORPORATE', 'Corporate', 'CLINIC,HOSPITAL', 5, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a111', 'ORGANISATION_TYPE', 'STANDALONE_CLINIC', 'Standalone clinic', 'CLINIC', 1, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a112', 'ORGANISATION_TYPE', 'MULTI_SPECIALITY_CLINIC', 'Multi-speciality clinic', 'CLINIC', 2, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a113', 'ORGANISATION_TYPE', 'CLINIC_CHAIN', 'Clinic chain', 'CLINIC', 3, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a114', 'ORGANISATION_TYPE', 'COMMUNITY_HEALTH_CENTRE', 'Community health centre', 'HOSPITAL', 4, TRUE),
    ('5a39a56c-3f45-4c7f-86c4-3fd5f8b5a115', 'ORGANISATION_TYPE', 'TEACHING_HOSPITAL', 'Teaching hospital', 'HOSPITAL', 5, TRUE)
ON CONFLICT (category, code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    provider_types = EXCLUDED.provider_types,
    display_order = EXCLUDED.display_order,
    active = EXCLUDED.active;
