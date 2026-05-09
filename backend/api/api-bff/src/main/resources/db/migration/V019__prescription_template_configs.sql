create table if not exists prescription_template_configs (
    id uuid primary key,
    tenant_id uuid not null,
    template_version integer not null,
    active boolean not null,
    clinic_logo_document_id uuid,
    header_text text,
    footer_text text,
    primary_color varchar(16),
    accent_color varchar(16),
    disclaimer text,
    doctor_signature_text text,
    show_qr_code boolean not null default true,
    watermark_text varchar(255),
    changed_by_app_user_id uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version integer not null default 0
);

create index if not exists ix_prescription_template_configs_tenant on prescription_template_configs (tenant_id, active);
create unique index if not exists uq_prescription_template_configs_tenant_version on prescription_template_configs (tenant_id, template_version);
