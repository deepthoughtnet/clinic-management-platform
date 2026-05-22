ALTER TABLE pharmacy_reconciliations
    ADD COLUMN IF NOT EXISTS submitted_by uuid,
    ADD COLUMN IF NOT EXISTS submitted_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS reviewed_by uuid,
    ADD COLUMN IF NOT EXISTS review_decision varchar(24),
    ADD COLUMN IF NOT EXISTS review_reason text,
    ADD COLUMN IF NOT EXISTS posted_by uuid,
    ADD COLUMN IF NOT EXISTS posted_at timestamp(6) with time zone;
