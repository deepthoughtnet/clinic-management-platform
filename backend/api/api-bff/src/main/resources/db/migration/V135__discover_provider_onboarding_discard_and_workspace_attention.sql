ALTER TABLE discover_provider_applications
    DROP CONSTRAINT IF EXISTS ck_discover_provider_status;

ALTER TABLE discover_provider_applications
    ADD CONSTRAINT ck_discover_provider_status CHECK (
        status IN (
            'DRAFT',
            'CONTACT_VERIFIED',
            'PROFILE_INCOMPLETE',
            'READY_FOR_REVIEW',
            'SUBMITTED',
            'UNDER_REVIEW',
            'CHANGES_REQUESTED',
            'APPROVED',
            'PUBLISHED',
            'DISCARDED',
            'SUSPENDED',
            'ARCHIVED'
        )
    );

ALTER TABLE discover_provider_status_history
    ADD COLUMN IF NOT EXISTS actor_category VARCHAR(64);
