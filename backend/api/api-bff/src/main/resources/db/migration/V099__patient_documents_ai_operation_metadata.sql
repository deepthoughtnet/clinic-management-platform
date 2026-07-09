alter table patient_documents
    add column if not exists last_ai_retry_at timestamp with time zone,
    add column if not exists last_ai_retry_status varchar(32),
    add column if not exists last_ai_retry_message text,
    add column if not exists last_ai_retry_job_id uuid,
    add column if not exists last_memory_repair_at timestamp with time zone,
    add column if not exists last_memory_repair_status varchar(32),
    add column if not exists last_memory_repair_message text,
    add column if not exists last_memory_repair_by uuid,
    add column if not exists last_memory_repair_deleted_pending_concept_count integer,
    add column if not exists last_memory_repair_inserted_concept_count integer,
    add column if not exists last_memory_repair_skipped_accepted_concept_count integer,
    add column if not exists last_memory_repair_filtered_polluted_concept_count integer,
    add column if not exists last_memory_repair_corrected_values text;
