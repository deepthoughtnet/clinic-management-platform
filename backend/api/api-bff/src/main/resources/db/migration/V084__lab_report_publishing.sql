alter table if exists lab_orders
    add column if not exists report_published_at timestamptz,
    add column if not exists report_published_by_user_id uuid,
    add column if not exists report_delivery_status varchar(32),
    add column if not exists report_delivery_channels text,
    add column if not exists report_delivery_notes text;

create index if not exists ix_lab_orders_tenant_report_published_at
    on lab_orders (tenant_id, report_published_at);
