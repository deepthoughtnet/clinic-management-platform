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
