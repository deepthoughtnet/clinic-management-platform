create table if not exists patient_longitudinal_concepts (
    id uuid not null primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    source_document_id uuid,
    source_document_type varchar(64),
    source_document_title varchar(256),
    source_document_date date,
    concept_family varchar(64) not null,
    concept_key varchar(128) not null,
    concept_label varchar(256) not null,
    value_text text,
    value_unit varchar(32),
    evidence_text text,
    source_summary text,
    verification_status varchar(32) not null,
    confidence numeric(6,4),
    review_notes text,
    override_reason text,
    reviewed_by_app_user_id uuid,
    reviewed_at timestamp with time zone,
    observed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists ix_patient_longitudinal_concepts_tenant_patient
    on patient_longitudinal_concepts (tenant_id, patient_id);

create index if not exists ix_patient_longitudinal_concepts_tenant_patient_status
    on patient_longitudinal_concepts (tenant_id, patient_id, verification_status);

create index if not exists ix_patient_longitudinal_concepts_tenant_patient_key
    on patient_longitudinal_concepts (tenant_id, patient_id, concept_key);

create index if not exists ix_patient_longitudinal_concepts_source_document
    on patient_longitudinal_concepts (tenant_id, source_document_id);
