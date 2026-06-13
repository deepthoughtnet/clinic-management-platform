create table if not exists lab_tests (
    id uuid primary key,
    tenant_id uuid not null,
    test_code varchar(64) not null,
    test_name varchar(256) not null,
    category varchar(128) not null,
    department varchar(128),
    sample_type varchar(128),
    unit varchar(64),
    reference_range varchar(256),
    turnaround_time varchar(128),
    price numeric(18,2),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_lab_tests_tenant_code on lab_tests (tenant_id, test_code);
create unique index if not exists uq_lab_tests_tenant_name on lab_tests (tenant_id, test_name);
create index if not exists ix_lab_tests_tenant_active on lab_tests (tenant_id, active);
create index if not exists ix_lab_tests_tenant_category on lab_tests (tenant_id, category);

create table if not exists lab_orders (
    id uuid primary key,
    tenant_id uuid not null,
    order_number varchar(64) not null,
    patient_id uuid not null,
    patient_number varchar(64),
    patient_name varchar(256),
    doctor_user_id uuid,
    doctor_name varchar(256),
    consultation_id uuid,
    notes text,
    status varchar(32) not null default 'ORDERED',
    ordered_at timestamptz not null default now(),
    bill_id uuid,
    payment_collected_at timestamptz,
    ready_for_collection_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_lab_orders_tenant_order_number on lab_orders (tenant_id, order_number);
create index if not exists ix_lab_orders_tenant_status on lab_orders (tenant_id, status);
create index if not exists ix_lab_orders_tenant_patient on lab_orders (tenant_id, patient_id);
create index if not exists ix_lab_orders_tenant_consultation on lab_orders (tenant_id, consultation_id);

create table if not exists lab_order_items (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    lab_test_id uuid,
    test_code varchar(64) not null,
    test_name varchar(256) not null,
    category varchar(128) not null,
    department varchar(128),
    sample_type varchar(128),
    unit varchar(64),
    reference_range varchar(256),
    turnaround_time varchar(128),
    price numeric(18,2) not null,
    sort_order integer not null,
    created_at timestamptz not null default now()
);

create index if not exists ix_lab_order_items_tenant_order on lab_order_items (tenant_id, lab_order_id);
