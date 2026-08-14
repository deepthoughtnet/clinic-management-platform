create table if not exists patient_portal_access_requests (
    id uuid primary key,
    tenant_id uuid not null,
    request_type varchar(32) not null,
    full_name varchar(256) not null,
    mobile varchar(64) not null,
    mobile_normalized varchar(32) not null,
    email varchar(256),
    note text,
    status varchar(32) not null,
    requested_at timestamptz not null,
    reviewed_at timestamptz,
    approved_at timestamptz,
    activated_at timestamptz,
    revoked_at timestamptz,
    reviewed_by uuid,
    reviewed_by_display_name varchar(256),
    rejection_reason text,
    linked_patient_id uuid,
    linked_patient_display_name varchar(256),
    access_code_hash varchar(256),
    access_code_issued_at timestamptz,
    access_code_expires_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index if not exists ix_patient_portal_access_requests_tenant_requested
    on patient_portal_access_requests (tenant_id, requested_at);

create index if not exists ix_patient_portal_access_requests_tenant_mobile
    on patient_portal_access_requests (tenant_id, mobile_normalized, requested_at);

create index if not exists ix_patient_portal_access_requests_tenant_status
    on patient_portal_access_requests (tenant_id, status, requested_at);
