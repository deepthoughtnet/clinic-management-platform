create table if not exists bills (
    id uuid primary key,
    tenant_id uuid not null,
    bill_number varchar(64) not null,
    patient_id uuid not null,
    consultation_id uuid null,
    appointment_id uuid null,
    bill_date date not null,
    status varchar(24) not null,
    subtotal_amount numeric(18,2) not null default 0,
    discount_amount numeric(18,2) not null default 0,
    tax_amount numeric(18,2) not null default 0,
    total_amount numeric(18,2) not null default 0,
    paid_amount numeric(18,2) not null default 0,
    due_amount numeric(18,2) not null default 0,
    notes text null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index if not exists uq_bills_tenant_bill_number on bills (tenant_id, bill_number);
create index if not exists ix_bills_tenant_patient on bills (tenant_id, patient_id);
create index if not exists ix_bills_tenant_status on bills (tenant_id, status);

create table if not exists bill_line_items (
    id uuid primary key,
    tenant_id uuid not null,
    bill_id uuid not null references bills(id) on delete cascade,
    item_type varchar(24) not null,
    item_name varchar(256) not null,
    quantity integer not null,
    unit_price numeric(18,2) not null,
    total_price numeric(18,2) not null,
    reference_id uuid null,
    sort_order integer not null
);

create index if not exists ix_bill_line_items_tenant_bill on bill_line_items (tenant_id, bill_id);
create index if not exists ix_bill_line_items_tenant_sort on bill_line_items (tenant_id, bill_id, sort_order);

create table if not exists bill_payments (
    id uuid primary key,
    tenant_id uuid not null,
    bill_id uuid not null references bills(id) on delete cascade,
    payment_date date not null,
    amount numeric(18,2) not null,
    payment_mode varchar(24) not null,
    reference_number varchar(128) null,
    notes text null,
    created_at timestamp with time zone not null
);

create index if not exists ix_bill_payments_tenant_bill on bill_payments (tenant_id, bill_id);
create index if not exists ix_bill_payments_tenant_date on bill_payments (tenant_id, payment_date);

create table if not exists bill_receipts (
    id uuid primary key,
    tenant_id uuid not null,
    receipt_number varchar(64) not null,
    bill_id uuid not null references bills(id) on delete cascade,
    payment_id uuid not null references bill_payments(id) on delete cascade,
    receipt_date date not null,
    amount numeric(18,2) not null,
    created_at timestamp with time zone not null
);

create unique index if not exists uq_bill_receipts_tenant_receipt_number on bill_receipts (tenant_id, receipt_number);
create index if not exists ix_bill_receipts_tenant_bill on bill_receipts (tenant_id, bill_id);
create index if not exists ix_bill_receipts_tenant_payment on bill_receipts (tenant_id, payment_id);
