alter table if exists lab_orders
    add column if not exists lab_verified_at timestamptz,
    add column if not exists lab_verified_by uuid,
    add column if not exists lab_verification_decision varchar(32),
    add column if not exists lab_verification_comments text,
    add column if not exists lab_verification_reason varchar(128);

create index if not exists ix_lab_orders_tenant_verification_status
    on lab_orders (tenant_id, status, lab_verified_at);
