ALTER TABLE discover_public_profile_submissions
    ADD COLUMN IF NOT EXISTS assigned_reviewer_reference VARCHAR(256),
    ADD COLUMN IF NOT EXISTS assigned_reviewer_display_name VARCHAR(256),
    ADD COLUMN IF NOT EXISTS assigned_reviewer_email VARCHAR(256);

ALTER TABLE discover_public_profile_review_findings
    ADD COLUMN IF NOT EXISTS provider_facing_message VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS internal_note VARCHAR(1000);

UPDATE discover_public_profile_submissions
SET assigned_reviewer_id = '00da4fe7-6f6d-4a80-91bf-48186214f1ba',
    assigned_reviewer_reference = 'platform.admin@clinic.local',
    assigned_reviewer_display_name = 'Platform Admin',
    assigned_reviewer_email = 'platform.admin@clinic.local'
WHERE submission_reference = 'ac2dc7a4-bae1-4043-bf22-1701329ae8ec'
  AND moderation_status = 'UNDER_REVIEW'
  AND assigned_at IS NOT NULL
  AND assigned_reviewer_id IS NULL;

UPDATE discover_public_profile_review_findings
SET provider_facing_message = COALESCE(provider_facing_message, reviewer_note),
    internal_note = COALESCE(internal_note, reviewer_note)
WHERE provider_facing_message IS NULL
   OR internal_note IS NULL;
