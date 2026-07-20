ALTER TABLE carepilot_campaign_executions
    ADD COLUMN IF NOT EXISTS acquired_at TIMESTAMP WITH TIME ZONE;
