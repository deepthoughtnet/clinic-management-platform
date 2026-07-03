create table if not exists lab_order_samples (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    lab_order_item_id uuid references lab_order_items(id) on delete set null,
    accession_number varchar(64) not null,
    barcode_value varchar(128) not null,
    specimen_type varchar(128) not null,
    container_type varchar(128),
    status varchar(32) not null,
    collected_at timestamptz,
    collected_by uuid,
    received_at timestamptz,
    received_by uuid,
    rejected_at timestamptz,
    rejected_by uuid,
    rejection_reason varchar(128),
    recollection_required boolean not null default false,
    notes text,
    created_at timestamptz not null default now(),
    created_by uuid,
    updated_at timestamptz,
    updated_by uuid
);

create index if not exists ix_lab_order_samples_tenant on lab_order_samples (tenant_id);
create index if not exists ix_lab_order_samples_order on lab_order_samples (tenant_id, lab_order_id);
create unique index if not exists uq_lab_order_samples_tenant_accession on lab_order_samples (tenant_id, accession_number);
create unique index if not exists uq_lab_order_samples_tenant_barcode on lab_order_samples (tenant_id, barcode_value);
create index if not exists ix_lab_order_samples_status on lab_order_samples (tenant_id, status);
create index if not exists ix_lab_order_samples_collected_at on lab_order_samples (tenant_id, collected_at);
