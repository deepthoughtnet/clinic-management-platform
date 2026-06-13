alter table if exists lab_orders
    add column if not exists doctor_reviewed_at timestamptz;

alter table if exists lab_orders
    add column if not exists doctor_reviewed_by_user_id uuid;

alter table if exists lab_orders
    add column if not exists doctor_reviewed_by varchar(256);

alter table if exists lab_orders
    add column if not exists doctor_comments text;

create index if not exists ix_lab_orders_tenant_doctor_reviewed
    on lab_orders (tenant_id, doctor_reviewed_at);
