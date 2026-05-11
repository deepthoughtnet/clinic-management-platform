alter table if exists medicine_catalogue
    add column if not exists generic_name varchar(256),
    add column if not exists brand_name varchar(256),
    add column if not exists category varchar(128),
    add column if not exists dosage_form varchar(64),
    add column if not exists unit varchar(32),
    add column if not exists manufacturer varchar(256),
    add column if not exists tax_rate numeric(5,2);

alter table if exists inventory_stocks
    add column if not exists purchase_date date,
    add column if not exists supplier_name varchar(256),
    add column if not exists quantity_received integer not null default 0,
    add column if not exists purchase_price numeric(18,2);

alter table if exists inventory_transactions
    add column if not exists reason text,
    add column if not exists created_by uuid;

create table if not exists prescription_dispensations (
    id uuid primary key,
    tenant_id uuid not null,
    prescription_id uuid not null,
    patient_id uuid not null,
    billing_status varchar(24) not null,
    billed_bill_id uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index if not exists uq_prescription_dispensations_tenant_prescription
    on prescription_dispensations (tenant_id, prescription_id);
create index if not exists ix_prescription_dispensations_tenant_patient
    on prescription_dispensations (tenant_id, patient_id);

create table if not exists prescription_dispense_items (
    id uuid primary key,
    tenant_id uuid not null,
    dispensation_id uuid not null references prescription_dispensations(id) on delete cascade,
    prescription_id uuid not null,
    prescription_medicine_id uuid,
    medicine_id uuid not null,
    prescribed_medicine_name varchar(256) not null,
    prescribed_sort_order integer,
    prescribed_quantity integer not null default 0,
    dispensed_quantity integer not null default 0,
    last_batch_id uuid,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists ix_prescription_dispense_items_tenant_disp
    on prescription_dispense_items (tenant_id, dispensation_id);
create index if not exists ix_prescription_dispense_items_tenant_presc
    on prescription_dispense_items (tenant_id, prescription_id);

