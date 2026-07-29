-- Discover domain owner: provider location coordinates for map selection and public profile display.

ALTER TABLE discover_provider_locations
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10,6),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10,6);

