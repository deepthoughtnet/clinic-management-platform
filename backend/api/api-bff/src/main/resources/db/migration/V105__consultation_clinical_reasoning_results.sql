create table if not exists consultation_clinical_reasoning_results (
    id uuid primary key,
    tenant_id uuid not null,
    consultation_id uuid not null,
    patient_id uuid not null,
    version_number integer not null,
    status varchar(24) not null,
    context_hash varchar(64) not null,
    prompt_version varchar(64) not null,
    reasoning_engine_version varchar(64) not null,
    provider varchar(64),
    model varchar(128),
    generated_by_app_user_id uuid,
    generated_by_display_name varchar(255),
    generated_at timestamptz not null,
    result_json text not null,
    superseded_by_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version integer not null default 0
);

create index if not exists ix_consultation_clinical_reasoning_tenant_consultation
    on consultation_clinical_reasoning_results (tenant_id, consultation_id);

create index if not exists ix_consultation_clinical_reasoning_tenant_status
    on consultation_clinical_reasoning_results (tenant_id, consultation_id, status);

create index if not exists ix_consultation_clinical_reasoning_tenant_context
    on consultation_clinical_reasoning_results (tenant_id, consultation_id, context_hash);

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'uq_consultation_clinical_reasoning_tenant_consultation_version'
    ) then
        alter table consultation_clinical_reasoning_results
            add constraint uq_consultation_clinical_reasoning_tenant_consultation_version
                unique (tenant_id, consultation_id, version_number);
    end if;
end $$;
