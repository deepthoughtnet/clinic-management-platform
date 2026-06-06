ALTER TABLE carepilot_webinars
    ADD COLUMN IF NOT EXISTS campaign_id UUID;

CREATE INDEX IF NOT EXISTS ix_cp_webinars_tenant_campaign
    ON carepilot_webinars (tenant_id, campaign_id);

CREATE INDEX IF NOT EXISTS ix_cp_webreg_tenant_lead
    ON carepilot_webinar_registrations (tenant_id, lead_id);
