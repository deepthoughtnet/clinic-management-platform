create table if not exists consultation_soap_notes (
    id uuid primary key,
    tenant_id uuid not null,
    consultation_id uuid not null,
    version_number integer not null,
    status varchar(24) not null,
    source varchar(24) not null,
    subjective text,
    objective text,
    assessment text,
    plan text,
    ai_provider varchar(64),
    ai_model varchar(128),
    generated_by_app_user_id uuid,
    accepted_by_app_user_id uuid,
    generated_at timestamptz,
    accepted_at timestamptz,
    superseded_by_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version integer not null default 0
);

create index if not exists ix_consultation_soap_notes_tenant_consultation
    on consultation_soap_notes (tenant_id, consultation_id);

create index if not exists ix_consultation_soap_notes_tenant_status
    on consultation_soap_notes (tenant_id, consultation_id, status);

create index if not exists ix_consultation_soap_notes_tenant_source
    on consultation_soap_notes (tenant_id, consultation_id, source);

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'uq_consultation_soap_notes_tenant_consultation_version'
    ) then
        alter table consultation_soap_notes
            add constraint uq_consultation_soap_notes_tenant_consultation_version
                unique (tenant_id, consultation_id, version_number);
    end if;
end $$;
