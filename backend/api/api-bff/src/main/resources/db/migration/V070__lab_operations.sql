alter table if exists lab_orders
    add column if not exists sample_type varchar(128),
    add column if not exists sample_collected_at timestamptz,
    add column if not exists sample_collected_by_user_id uuid,
    add column if not exists sample_collected_by varchar(256),
    add column if not exists sample_collection_notes text,
    add column if not exists processing_started_at timestamptz,
    add column if not exists result_entered_at timestamptz,
    add column if not exists result_comments text,
    add column if not exists report_generated_at timestamptz,
    add column if not exists report_generated_by_user_id uuid,
    add column if not exists report_filename varchar(256);

create index if not exists ix_lab_orders_tenant_sample_status on lab_orders (tenant_id, status, sample_collected_at);
create index if not exists ix_lab_orders_tenant_results_status on lab_orders (tenant_id, status, result_entered_at);

create table if not exists lab_order_results (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    lab_order_item_id uuid references lab_order_items(id) on delete cascade,
    test_code varchar(64) not null,
    test_name varchar(256) not null,
    component_name varchar(256),
    result_value varchar(256),
    unit varchar(64),
    reference_range varchar(256),
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists ix_lab_order_results_tenant_order on lab_order_results (tenant_id, lab_order_id);
create index if not exists ix_lab_order_results_tenant_item on lab_order_results (tenant_id, lab_order_item_id);
