alter table patient_clinical_documents
    add column if not exists ai_extraction_provider varchar(64),
    add column if not exists ai_extraction_model varchar(128),
    add column if not exists ai_extraction_confidence numeric(5,4),
    add column if not exists ai_extraction_summary text,
    add column if not exists ai_extraction_structured_json text,
    add column if not exists ai_extraction_review_notes text,
    add column if not exists ai_extraction_reviewed_by_app_user_id uuid,
    add column if not exists ai_extraction_reviewed_at timestamp with time zone;

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

create index if not exists ix_clinical_ai_jobs_tenant_status on clinical_ai_jobs (tenant_id, status);
create index if not exists ix_clinical_ai_jobs_tenant_document on clinical_ai_jobs (tenant_id, document_id);
create index if not exists ix_clinical_ai_jobs_next_attempt on clinical_ai_jobs (next_attempt_at);
