create table if not exists discover_provider_access_requests (
    id uuid primary key,
    request_type varchar(32) not null,
    provider_type varchar(32) not null,
    full_name varchar(256) not null,
    email varchar(256),
    email_normalized varchar(256),
    mobile varchar(64) not null,
    mobile_normalized varchar(32) not null,
    provider_application_reference varchar(64),
    note text,
    status varchar(32) not null,
    requested_at timestamptz not null,
    reviewed_at timestamptz,
    approved_at timestamptz,
    revoked_at timestamptz,
    reviewed_by uuid,
    reviewed_by_display_name varchar(256),
    rejection_reason text,
    linked_provider_account_id uuid,
    linked_provider_account_display_name varchar(256),
    linked_provider_application_reference varchar(64),
    access_code_hash varchar(256),
    access_code_issued_at timestamptz,
    access_code_expires_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index if not exists ix_discover_provider_access_requests_status_requested
    on discover_provider_access_requests (status, requested_at);

create index if not exists ix_discover_provider_access_requests_provider_mobile
    on discover_provider_access_requests (provider_type, mobile_normalized, requested_at);

create index if not exists ix_discover_provider_access_requests_provider_email
    on discover_provider_access_requests (provider_type, email_normalized, requested_at);

create index if not exists ix_discover_provider_access_requests_provider_application_ref
    on discover_provider_access_requests (provider_application_reference, requested_at);
