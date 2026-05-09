alter table patient_clinical_documents
    add column if not exists ai_extraction_accepted_json text,
    add column if not exists ai_extraction_override_reason text;

alter table clinical_ai_jobs
    add column if not exists review_status varchar(32),
    add column if not exists review_notes text,
    add column if not exists reviewed_by_app_user_id uuid,
    add column if not exists approved_by_app_user_id uuid,
    add column if not exists reviewed_at timestamp with time zone,
    add column if not exists approved_at timestamp with time zone;
