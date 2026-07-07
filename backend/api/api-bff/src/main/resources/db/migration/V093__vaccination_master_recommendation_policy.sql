ALTER TABLE vaccines
    ADD COLUMN IF NOT EXISTS recommendation_policy varchar(32),
    ADD COLUMN IF NOT EXISTS catch_up_policy varchar(32),
    ADD COLUMN IF NOT EXISTS catch_up_max_age_days integer,
    ADD COLUMN IF NOT EXISTS applicable_age_group varchar(32),
    ADD COLUMN IF NOT EXISTS clinical_indications text;

CREATE INDEX IF NOT EXISTS ix_vaccines_tenant_recommendation_policy
    ON vaccines (tenant_id, recommendation_policy, active);
