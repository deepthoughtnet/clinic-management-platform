alter table consultation_soap_notes
    add column if not exists source_hash varchar(64);

create index if not exists ix_consultation_soap_notes_tenant_source_hash
    on consultation_soap_notes (tenant_id, consultation_id, source_hash);
