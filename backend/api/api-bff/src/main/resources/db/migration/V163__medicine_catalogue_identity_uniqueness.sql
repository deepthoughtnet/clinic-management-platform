DO $$
DECLARE
    duplicate_row RECORD;
BEGIN
    SELECT tenant_id, medicine_name, medicine_type, strength, COUNT(*) AS duplicate_count
    INTO duplicate_row
    FROM medicine_catalogue
    GROUP BY tenant_id, medicine_name, medicine_type, strength
    HAVING COUNT(*) > 1
    ORDER BY COUNT(*) DESC, tenant_id, medicine_name, medicine_type, strength
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Duplicate medicine catalogue rows exist for tenant_id=%, medicine_name=%, medicine_type=%, strength=% (count=%). Clean up duplicates before applying V163.',
            duplicate_row.tenant_id,
            duplicate_row.medicine_name,
            duplicate_row.medicine_type,
            duplicate_row.strength,
            duplicate_row.duplicate_count;
    END IF;
END
$$;

ALTER TABLE medicine_catalogue
    DROP CONSTRAINT IF EXISTS uq_medicine_catalogue_tenant_name;

ALTER TABLE medicine_catalogue
    ADD CONSTRAINT uq_medicine_catalogue_tenant_name_type_strength
        UNIQUE (tenant_id, medicine_name, medicine_type, strength);
