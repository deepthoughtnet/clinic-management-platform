ALTER TABLE discover_provider_applications
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE discover_provider_submissions
    ADD COLUMN status_before VARCHAR(32),
    ADD COLUMN status_after VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    ADD COLUMN submitted_by VARCHAR(64) NOT NULL DEFAULT 'PROVIDER',
    ADD COLUMN snapshot_hash VARCHAR(128) NOT NULL DEFAULT '',
    ADD COLUMN snapshot_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN submission_note VARCHAR(512);

CREATE TABLE discover_provider_change_requests (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES discover_provider_applications(id) ON DELETE CASCADE,
    submission_version_number INTEGER,
    requested_sections VARCHAR(512),
    reviewer_message VARCHAR(1000),
    provider_response_note VARCHAR(1000),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX ix_discover_provider_change_requests_provider ON discover_provider_change_requests(provider_id, requested_at DESC);
CREATE INDEX ix_discover_provider_change_requests_open ON discover_provider_change_requests(provider_id, resolved_at);
