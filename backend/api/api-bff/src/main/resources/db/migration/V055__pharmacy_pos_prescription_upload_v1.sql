alter table pharmacy_sales
    add column if not exists prescription_document_id uuid,
    add column if not exists prescription_file_name varchar(512),
    add column if not exists prescription_uploaded_at timestamptz;

create index if not exists ix_pharmacy_sales_tenant_prescription
    on pharmacy_sales (tenant_id, prescription_document_id);

create table if not exists pharmacy_sale_prescriptions (
    id uuid primary key,
    tenant_id uuid not null,
    linked_sale_id uuid null references pharmacy_sales(id) on delete set null,
    uploaded_by_app_user_id uuid not null,
    original_filename varchar(512) not null,
    media_type varchar(128) not null,
    size_bytes bigint not null,
    checksum_sha256 varchar(64) not null,
    storage_key varchar(1024) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_pharmacy_sale_prescriptions_storage_key unique (tenant_id, storage_key)
);

create index if not exists ix_pharmacy_sale_prescriptions_tenant_created
    on pharmacy_sale_prescriptions (tenant_id, created_at desc);

create index if not exists ix_pharmacy_sale_prescriptions_tenant_sale
    on pharmacy_sale_prescriptions (tenant_id, linked_sale_id);
