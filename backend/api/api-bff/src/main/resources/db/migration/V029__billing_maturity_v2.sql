alter table if exists bills
    add column if not exists discount_type varchar(24) not null default 'NONE',
    add column if not exists discount_value numeric(18,2) not null default 0,
    add column if not exists discount_reason text,
    add column if not exists discount_approved_by uuid,
    add column if not exists refunded_amount numeric(18,2) not null default 0,
    add column if not exists net_paid_amount numeric(18,2) not null default 0,
    add column if not exists invoice_emailed_at timestamp with time zone;

alter table if exists bill_line_items
    add column if not exists line_discount_amount numeric(18,2) not null default 0,
    add column if not exists batch_number varchar(128),
    add column if not exists dispensation_reference_id uuid;

alter table if exists bill_payments
    add column if not exists payment_datetime timestamp with time zone,
    add column if not exists received_by uuid;

create table if not exists bill_refunds (
    id uuid primary key,
    tenant_id uuid not null,
    bill_id uuid not null references bills(id) on delete cascade,
    payment_id uuid,
    amount numeric(18,2) not null,
    reason text not null,
    refund_mode varchar(24) not null,
    notes text,
    refunded_by uuid,
    refunded_at timestamp with time zone not null,
    created_at timestamp with time zone not null
);

create index if not exists ix_bill_refunds_tenant_bill on bill_refunds (tenant_id, bill_id);
create index if not exists ix_bill_refunds_tenant_created on bill_refunds (tenant_id, created_at);
