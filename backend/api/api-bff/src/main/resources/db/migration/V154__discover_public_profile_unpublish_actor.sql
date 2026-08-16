ALTER TABLE discover_public_profile_publications
    ADD COLUMN IF NOT EXISTS unpublished_by VARCHAR(160);

UPDATE discover_public_profile_publications
SET unpublished_by = COALESCE(unpublished_by, published_by)
WHERE publication_status = 'UNPUBLISHED'
  AND unpublished_at IS NOT NULL
  AND unpublished_by IS NULL;

