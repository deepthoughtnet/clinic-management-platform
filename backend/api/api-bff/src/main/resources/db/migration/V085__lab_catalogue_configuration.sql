create table if not exists lab_category_settings (
    id uuid primary key,
    tenant_id uuid not null,
    category_code varchar(64) not null,
    display_name varchar(128) not null,
    active boolean not null default true,
    display_order integer,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index if not exists uq_lab_category_settings_tenant_code
    on lab_category_settings (tenant_id, category_code);

create index if not exists ix_lab_category_settings_tenant_active
    on lab_category_settings (tenant_id, active);

create index if not exists ix_lab_category_settings_tenant_display_order
    on lab_category_settings (tenant_id, display_order);

alter table if exists lab_tests
    add column if not exists enabled boolean not null default true,
    add column if not exists tenant_price_override numeric(18,2),
    add column if not exists tenant_tat_override varchar(128),
    add column if not exists display_order integer;

create index if not exists ix_lab_tests_tenant_enabled
    on lab_tests (tenant_id, enabled);

create index if not exists ix_lab_tests_tenant_display_order
    on lab_tests (tenant_id, display_order);
