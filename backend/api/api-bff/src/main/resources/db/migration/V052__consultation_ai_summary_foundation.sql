create table if not exists consultation_ai_summaries (
    id uuid not null primary key,
    tenant_id uuid not null,
    consultation_id uuid not null,
    summary text,
    provider varchar(32),
    model varchar(64),
    generated_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version integer not null default 0
);

create unique index if not exists uq_consultation_ai_summaries_tenant_consultation
    on consultation_ai_summaries (tenant_id, consultation_id);

create index if not exists ix_consultation_ai_summaries_tenant_consultation
    on consultation_ai_summaries (tenant_id, consultation_id);
