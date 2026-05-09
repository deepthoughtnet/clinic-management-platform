create table if not exists patient_clinical_documents (
    id uuid primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    consultation_id uuid,
    appointment_id uuid,
    uploaded_by_app_user_id uuid not null,
    document_type varchar(48) not null,
    original_filename varchar(512) not null,
    media_type varchar(128) not null,
    size_bytes bigint not null,
    checksum_sha256 varchar(64) not null,
    storage_key varchar(1024) not null,
    notes text,
    referred_doctor varchar(255),
    referred_hospital varchar(255),
    referral_notes text,
    ai_extraction_status varchar(32),
    ocr_status varchar(32),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version integer not null default 0,
    constraint uq_patient_clinical_documents_storage_key unique (tenant_id, storage_key)
);

create index if not exists ix_patient_clinical_documents_tenant_patient on patient_clinical_documents (tenant_id, patient_id, created_at desc);
create index if not exists ix_patient_clinical_documents_tenant_type on patient_clinical_documents (tenant_id, document_type);
create index if not exists ix_patient_clinical_documents_tenant_uploaded_by on patient_clinical_documents (tenant_id, uploaded_by_app_user_id);
