ALTER TABLE discover_provider_services
    DROP CONSTRAINT IF EXISTS ck_discover_provider_service_type;

CREATE TEMP TABLE discover_provider_service_type_map (
    normalized_service_type VARCHAR(64) PRIMARY KEY,
    canonical_service_type VARCHAR(64) NOT NULL
) ON COMMIT DROP;

INSERT INTO discover_provider_service_type_map (normalized_service_type, canonical_service_type) VALUES
    ('CONSULTATION', 'CONSULTATION'),
    ('CONSULTATIONS', 'CONSULTATION'),
    ('GENERAL_CONSULTATION', 'CONSULTATION'),
    ('IN_PERSON_CONSULTATION', 'CONSULTATION'),
    ('TELECONSULTATION', 'TELECONSULTATION'),
    ('TELE_CONSULTATION', 'TELECONSULTATION'),
    ('VIDEO_CONSULTATION', 'TELECONSULTATION'),
    ('ONLINE_CONSULTATION', 'TELECONSULTATION'),
    ('HEALTH_CHECKUP', 'HEALTH_CHECKUPS'),
    ('HEALTH_CHECK_UP', 'HEALTH_CHECKUPS'),
    ('HEALTH_CHECKUPS', 'HEALTH_CHECKUPS'),
    ('VACCINE', 'VACCINATION'),
    ('VACCINATIONS', 'VACCINATION'),
    ('VACCINATION', 'VACCINATION'),
    ('MINOR_PROCEDURE', 'MINOR_PROCEDURES'),
    ('PROCEDURES', 'MINOR_PROCEDURES'),
    ('MINOR_PROCEDURES', 'MINOR_PROCEDURES'),
    ('HOME_VISIT', 'HOME_VISIT'),
    ('HOME_VISITS', 'HOME_VISIT'),
    ('HOME_CONSULTATION', 'HOME_VISIT'),
    ('LAB', 'LAB_COLLECTION'),
    ('LAB_TEST', 'LAB_COLLECTION'),
    ('LAB_TESTS', 'LAB_COLLECTION'),
    ('LAB_COLLECTION', 'LAB_COLLECTION'),
    ('LAB_COLLECTIONS', 'LAB_COLLECTION'),
    ('SAMPLE_COLLECTION', 'LAB_COLLECTION'),
    ('CHRONIC_CARE', 'CHRONIC_DISEASE_MANAGEMENT'),
    ('CHRONIC_DISEASE', 'CHRONIC_DISEASE_MANAGEMENT'),
    ('CHRONIC_DISEASE_MANAGEMENT', 'CHRONIC_DISEASE_MANAGEMENT'),
    ('PREVENTIVE_HEALTH', 'PREVENTIVE_CARE'),
    ('PREVENTIVE_SERVICES', 'PREVENTIVE_CARE'),
    ('PREVENTIVE_CARE', 'PREVENTIVE_CARE'),
    ('PHARMACY', 'PREVENTIVE_CARE'),
    ('RADIOLOGY', 'LAB_COLLECTION');

WITH normalized_rows AS (
    SELECT
        id,
        UPPER(REGEXP_REPLACE(TRIM(service_type), '[^A-Za-z0-9]+', '_', 'g')) AS normalized_service_type
    FROM discover_provider_services
)
UPDATE discover_provider_services service_row
SET service_type = map_row.canonical_service_type
FROM normalized_rows normalized_row
JOIN discover_provider_service_type_map map_row
    ON map_row.normalized_service_type = normalized_row.normalized_service_type
WHERE service_row.id = normalized_row.id
  AND service_row.service_type IS DISTINCT FROM map_row.canonical_service_type;

DO $$
DECLARE
    unexpected_values text;
BEGIN
    WITH inspected_rows AS (
        SELECT
            CASE
                WHEN service_type IS NULL THEN '<NULL>'
                WHEN TRIM(service_type) = '' THEN '<BLANK>'
                ELSE service_type
            END AS raw_value,
            CASE
                WHEN service_type IS NULL OR TRIM(service_type) = '' THEN NULL
                ELSE UPPER(REGEXP_REPLACE(TRIM(service_type), '[^A-Za-z0-9]+', '_', 'g'))
            END AS normalized_service_type
        FROM discover_provider_services
    ),
    unexpected_rows AS (
        SELECT
            raw_value,
            normalized_service_type,
            COUNT(*) AS row_count
        FROM inspected_rows
        WHERE normalized_service_type IS NULL
           OR NOT EXISTS (
                SELECT 1
                FROM discover_provider_service_type_map map_row
                WHERE map_row.normalized_service_type = inspected_rows.normalized_service_type
           )
        GROUP BY raw_value, normalized_service_type
    )
    SELECT string_agg(
            format('%s (normalized=%s, count=%s)', raw_value, COALESCE(normalized_service_type, '<NULL>'), row_count),
            '; '
            ORDER BY raw_value
    )
    INTO unexpected_values
    FROM unexpected_rows;

    IF unexpected_values IS NOT NULL THEN
        RAISE EXCEPTION 'Unexpected discover_provider_services.service_type values remain after canonicalization: %', unexpected_values;
    END IF;
END $$;

ALTER TABLE discover_provider_services
    ADD CONSTRAINT ck_discover_provider_service_type CHECK (service_type IN (
        'CONSULTATION',
        'TELECONSULTATION',
        'HEALTH_CHECKUPS',
        'VACCINATION',
        'MINOR_PROCEDURES',
        'HOME_VISIT',
        'LAB_COLLECTION',
        'CHRONIC_DISEASE_MANAGEMENT',
        'PREVENTIVE_CARE'
    ));
