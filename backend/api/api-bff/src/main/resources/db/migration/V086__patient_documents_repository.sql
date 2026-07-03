create table if not exists patient_documents (
    id uuid primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    consultation_id uuid,
    source_module varchar(64),
    source_entity_id varchar(128),
    document_type varchar(48) not null,
    title varchar(255) not null,
    description text,
    report_date date,
    uploaded_by_user_id uuid not null,
    uploaded_by_name varchar(255) not null,
    upload_source varchar(32) not null,
    file_name varchar(512) not null,
    content_type varchar(128) not null,
    file_size bigint not null,
    storage_bucket varchar(255) not null,
    storage_object_key varchar(1024) not null,
    checksum varchar(64),
    visibility varchar(32) not null,
    verification_status varchar(32) not null,
    ocr_status varchar(32) not null,
    ai_index_status varchar(32) not null,
    ai_extraction_status varchar(32),
    ai_extraction_provider varchar(64),
    ai_extraction_model varchar(128),
    ai_extraction_confidence numeric(5,4),
    ai_extraction_summary text,
    ai_extraction_structured_json text,
    ai_extraction_review_notes text,
    ai_extraction_accepted_json text,
    ai_extraction_override_reason text,
    ai_extraction_reviewed_by_user_id uuid,
    ai_extraction_reviewed_at timestamp with time zone,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    created_by uuid,
    updated_by uuid,
    version integer not null default 0,
    constraint uq_patient_documents_storage_object_key unique (tenant_id, storage_object_key)
);

create index if not exists ix_patient_documents_tenant_patient on patient_documents (tenant_id, patient_id, created_at desc);
create index if not exists ix_patient_documents_tenant_type on patient_documents (tenant_id, document_type);
create index if not exists ix_patient_documents_tenant_consultation on patient_documents (tenant_id, consultation_id);
create index if not exists ix_patient_documents_tenant_source on patient_documents (tenant_id, source_module, source_entity_id);
create index if not exists ix_patient_documents_tenant_uploaded_by on patient_documents (tenant_id, uploaded_by_user_id);
create index if not exists ix_patient_documents_tenant_active on patient_documents (tenant_id, active);
create index if not exists ix_patient_documents_tenant_report_date on patient_documents (tenant_id, report_date);
