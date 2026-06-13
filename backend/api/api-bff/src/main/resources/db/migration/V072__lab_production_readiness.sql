create table if not exists lab_test_parameters (
    id uuid primary key,
    tenant_id uuid not null,
    lab_test_id uuid not null references lab_tests(id) on delete cascade,
    parameter_name varchar(256) not null,
    unit varchar(64),
    normal_range varchar(256),
    critical_range varchar(256),
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_lab_test_parameters_tenant_test_name
    on lab_test_parameters (tenant_id, lab_test_id, parameter_name);
create index if not exists ix_lab_test_parameters_tenant_test
    on lab_test_parameters (tenant_id, lab_test_id, sort_order);

alter table if exists lab_orders
    add column if not exists external_lab_vendor varchar(256),
    add column if not exists external_reference_number varchar(128),
    add column if not exists delivered_at timestamptz,
    add column if not exists delivered_by_user_id uuid;

alter table if exists lab_order_results
    add column if not exists parameter_name varchar(256),
    add column if not exists result_flag varchar(32),
    add column if not exists critical_result boolean not null default false;

create index if not exists ix_lab_order_results_tenant_flag
    on lab_order_results (tenant_id, result_flag);

create table if not exists lab_order_attachments (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    attachment_type varchar(32) not null,
    original_filename varchar(256) not null,
    media_type varchar(128) not null,
    storage_key varchar(512),
    size_bytes bigint,
    checksum_sha256 varchar(128),
    dicom_metadata_json text,
    uploaded_by_user_id uuid,
    created_at timestamptz not null default now()
);

create index if not exists ix_lab_order_attachments_tenant_order
    on lab_order_attachments (tenant_id, lab_order_id, created_at desc);
