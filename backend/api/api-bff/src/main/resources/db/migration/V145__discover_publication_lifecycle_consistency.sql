ALTER TABLE discover_public_profile_publications
    ADD COLUMN published_by VARCHAR(160);

UPDATE discover_public_profile_publications publication
SET published_by = COALESCE(
        submission.decision_by_id::text,
        NULLIF(submission.assigned_reviewer_reference, ''),
        'system:lifecycle-reconciliation'
    )
FROM discover_public_profile_submissions submission
WHERE submission.submission_reference = publication.approved_submission_reference
  AND publication.published_by IS NULL;

UPDATE discover_public_profile_publications
SET published_by = 'system:lifecycle-reconciliation'
WHERE published_by IS NULL;

ALTER TABLE discover_public_profile_publications
    ALTER COLUMN published_by SET NOT NULL;

UPDATE discover_public_profile_submissions submission
SET publication_status_snapshot = 'PUBLISHED',
    published_at = publication.published_at,
    unpublished_at = NULL,
    updated_at = GREATEST(submission.updated_at, publication.updated_at),
    optimistic_lock_version = submission.optimistic_lock_version + 1
FROM discover_public_profile_publications publication
WHERE publication.public_profile_reference = submission.public_profile_reference
  AND publication.approved_submission_reference = submission.submission_reference
  AND publication.current_flag = TRUE
  AND publication.publication_status = 'PUBLISHED'
  AND (
      submission.publication_status_snapshot IS DISTINCT FROM 'PUBLISHED'
      OR submission.published_at IS DISTINCT FROM publication.published_at
      OR submission.unpublished_at IS NOT NULL
  );
