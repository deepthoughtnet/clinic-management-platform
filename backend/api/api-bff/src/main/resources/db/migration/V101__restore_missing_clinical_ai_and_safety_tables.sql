create table if not exists clinical_ai_jobs (
    id uuid primary key,
    tenant_id uuid not null,
    job_type varchar(32) not null,
    source_type varchar(64) not null,
    source_id uuid,
    document_id uuid,
    patient_id uuid,
    consultation_id uuid,
    status varchar(32) not null,
    provider varchar(64),
    model varchar(128),
    ocr_provider varchar(64),
    confidence numeric(5,4),
    request_json text,
    result_json text,
    summary_text text,
    review_status varchar(32),
    review_notes text,
    reviewed_by_app_user_id uuid,
    approved_by_app_user_id uuid,
    reviewed_at timestamp with time zone,
    approved_at timestamp with time zone,
    attempt_count integer not null default 0,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    next_attempt_at timestamp with time zone,
    error_message text,
    requested_by_app_user_id uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version integer not null default 0
);

alter table clinical_ai_jobs
    add column if not exists review_status varchar(32),
    add column if not exists review_notes text,
    add column if not exists reviewed_by_app_user_id uuid,
    add column if not exists approved_by_app_user_id uuid,
    add column if not exists reviewed_at timestamp with time zone,
    add column if not exists approved_at timestamp with time zone;

create index if not exists ix_clinical_ai_jobs_tenant_status on clinical_ai_jobs (tenant_id, status);
create index if not exists ix_clinical_ai_jobs_tenant_document on clinical_ai_jobs (tenant_id, document_id);
create index if not exists ix_clinical_ai_jobs_next_attempt on clinical_ai_jobs (next_attempt_at);

create table if not exists prescription_safety_reviews (
    id uuid not null primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    consultation_id uuid not null,
    prescription_id uuid not null,
    prescription_version integer not null,
    prescription_hash varchar(128) not null,
    patient_context_hash varchar(128) not null,
    rules_version varchar(64) not null,
    evaluation_id varchar(128) not null,
    evaluation_overall_severity varchar(24) not null,
    evaluation_snapshot_json text not null,
    decision_status varchar(48) not null,
    reviewed_by_app_user_id uuid,
    reviewed_at timestamp with time zone,
    acknowledgement_json text,
    override_reason_code varchar(64),
    override_reason_text text,
    override_category varchar(64),
    finalized_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version integer not null default 0
);

create unique index if not exists uq_prescription_safety_reviews_snapshot
    on prescription_safety_reviews (tenant_id, prescription_id, prescription_hash, patient_context_hash, rules_version);
create index if not exists ix_prescription_safety_reviews_tenant_prescription
    on prescription_safety_reviews (tenant_id, prescription_id, updated_at desc);
create index if not exists ix_prescription_safety_reviews_tenant_consultation
    on prescription_safety_reviews (tenant_id, consultation_id, updated_at desc);
create index if not exists ix_prescription_safety_reviews_tenant_patient
    on prescription_safety_reviews (tenant_id, patient_id, updated_at desc);
