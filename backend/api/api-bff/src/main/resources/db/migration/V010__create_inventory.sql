create table if not exists medicine_catalogue (
    id uuid primary key,
    tenant_id uuid not null,
    medicine_name varchar(256) not null,
    medicine_type varchar(24) not null,
    strength varchar(128),
    default_dosage varchar(128),
    default_frequency varchar(64),
    default_duration_days integer,
    default_timing varchar(24),
    default_instructions text,
    default_price numeric(18,2),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_medicine_catalogue_tenant_name unique (tenant_id, medicine_name)
);

create index if not exists ix_medicine_catalogue_tenant_active on medicine_catalogue (tenant_id, active);
create index if not exists ix_medicine_catalogue_tenant_name on medicine_catalogue (tenant_id, medicine_name);

create table if not exists inventory_stocks (
    id uuid primary key,
    tenant_id uuid not null,
    medicine_id uuid not null,
    batch_number varchar(128),
    expiry_date date,
    quantity_on_hand integer not null default 0,
    low_stock_threshold integer,
    unit_cost numeric(18,2),
    selling_price numeric(18,2),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists ix_inventory_stocks_tenant_medicine on inventory_stocks (tenant_id, medicine_id);
create index if not exists ix_inventory_stocks_tenant_active on inventory_stocks (tenant_id, active);

create table if not exists inventory_transactions (
    id uuid primary key,
    tenant_id uuid not null,
    medicine_id uuid not null,
    stock_batch_id uuid,
    transaction_type varchar(24) not null,
    quantity integer not null,
    reference_type varchar(64),
    reference_id uuid,
    notes text,
    created_at timestamptz not null
);

create index if not exists ix_inventory_transactions_tenant_medicine on inventory_transactions (tenant_id, medicine_id);
create index if not exists ix_inventory_transactions_tenant_created on inventory_transactions (tenant_id, created_at);
