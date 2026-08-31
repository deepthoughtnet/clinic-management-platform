create table if not exists consultation_ai_prescription_suggestions (
    id uuid not null primary key,
    tenant_id uuid not null,
    consultation_id uuid not null,
    version_number integer not null,
    status varchar(24) not null,
    source_hash varchar(64) not null,
    provider varchar(64),
    model varchar(128),
    generated_by_app_user_id uuid,
    generated_by_display_name varchar(255),
    generated_at timestamptz not null,
    content_json text not null,
    superseded_by_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version integer not null default 0
);

create index if not exists ix_consultation_ai_prescription_suggestions_tenant_consultation
    on consultation_ai_prescription_suggestions (tenant_id, consultation_id);

create index if not exists ix_consultation_ai_prescription_suggestions_tenant_status
    on consultation_ai_prescription_suggestions (tenant_id, consultation_id, status);

create index if not exists ix_consultation_ai_prescription_suggestions_tenant_context
    on consultation_ai_prescription_suggestions (tenant_id, consultation_id, source_hash);

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'uq_consultation_ai_prescription_suggestions_tenant_consultation_version'
    ) then
        alter table consultation_ai_prescription_suggestions
            add constraint uq_consultation_ai_prescription_suggestions_tenant_consultation_version
                unique (tenant_id, consultation_id, version_number);
    end if;
end $$;
