alter table if exists patient_vaccinations
    add column if not exists source varchar(16) not null default 'INTERNAL',
    add column if not exists external_place varchar(256) null,
    add column if not exists proof_document_id uuid null,
    add column if not exists verified_status varchar(32) not null default 'VERIFIED',
    add column if not exists verified_by_user_id uuid null,
    add column if not exists verified_at timestamp with time zone null;

update patient_vaccinations
set source = coalesce(nullif(source, ''), 'INTERNAL'),
    verified_status = coalesce(nullif(verified_status, ''), case when coalesce(nullif(source, ''), 'INTERNAL') = 'EXTERNAL' then 'UNVERIFIED' else 'VERIFIED' end)
where source is null or source = '' or verified_status is null or verified_status = '';

create index if not exists ix_patient_vaccinations_tenant_source on patient_vaccinations (tenant_id, source);
create index if not exists ix_patient_vaccinations_tenant_proof_document on patient_vaccinations (tenant_id, proof_document_id);
