create table if not exists commercial_plan_pricing (
    id uuid primary key,
    published_version_id uuid not null unique references commercial_plan_versions(id),
    currency char(3) not null,
    billing_cycle varchar(32) not null,
    monthly_price numeric(18,4) not null,
    annual_price numeric(18,4) not null,
    setup_fee numeric(18,4) not null,
    trial_days integer not null default 0,
    tax_model varchar(32) not null,
    tax_percentage numeric(18,4) not null default 0,
    discount_allowed boolean not null default false,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    created_by uuid,
    version bigint not null default 0,
    constraint ck_commercial_plan_pricing_currency check (currency in ('INR', 'USD', 'EUR')),
    constraint ck_commercial_plan_pricing_billing_cycle check (billing_cycle in ('MONTHLY', 'ANNUAL', 'QUARTERLY', 'ONE_TIME', 'TRIAL')),
    constraint ck_commercial_plan_pricing_tax_model check (tax_model in ('EXCLUSIVE', 'INCLUSIVE', 'NONE')),
    constraint ck_commercial_plan_pricing_status check (status in ('DRAFT', 'PUBLISHED', 'RETIRED')),
    constraint ck_commercial_plan_pricing_amounts check (
        monthly_price > 0
        and annual_price > 0
        and setup_fee >= 0
        and tax_percentage >= 0
        and annual_price <= monthly_price * 12
        and trial_days between 0 and 365
    )
);

create index if not exists ix_commercial_plan_pricing_status
    on commercial_plan_pricing (status);

create index if not exists ix_commercial_plan_pricing_created_at
    on commercial_plan_pricing (created_at desc);

create table if not exists commercial_plan_metered_rates (
    id uuid primary key,
    pricing_id uuid not null references commercial_plan_pricing(id) on delete cascade,
    limit_definition_id uuid not null references commercial_limit_definitions(id),
    included_quantity numeric(18,4) not null default 0,
    overage_enabled boolean not null default false,
    unit_price numeric(18,4) not null,
    unit_name varchar(128) not null,
    billing_rounding varchar(64),
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    created_by uuid,
    version bigint not null default 0,
    constraint ck_commercial_plan_metered_rates_status check (status in ('DRAFT', 'PUBLISHED', 'RETIRED')),
    constraint ck_commercial_plan_metered_rates_amounts check (included_quantity >= 0 and unit_price > 0)
);

create index if not exists ix_commercial_plan_metered_rates_pricing
    on commercial_plan_metered_rates (pricing_id);

create index if not exists ix_commercial_plan_metered_rates_limit
    on commercial_plan_metered_rates (limit_definition_id);

create table if not exists commercial_plan_addon_pricing (
    id uuid primary key,
    pricing_id uuid not null references commercial_plan_pricing(id) on delete cascade,
    addon_offer_id uuid not null references commercial_addon_offers(id),
    purchase_type varchar(32) not null,
    monthly_price numeric(18,4) not null default 0,
    annual_price numeric(18,4) not null default 0,
    one_time_price numeric(18,4) not null default 0,
    max_quantity integer,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    created_by uuid,
    version bigint not null default 0,
    constraint ck_commercial_plan_addon_pricing_purchase_type check (purchase_type in ('MONTHLY', 'ANNUAL', 'ONE_TIME')),
    constraint ck_commercial_plan_addon_pricing_status check (status in ('DRAFT', 'PUBLISHED', 'RETIRED')),
    constraint ck_commercial_plan_addon_pricing_amounts check (
        monthly_price >= 0
        and annual_price >= 0
        and one_time_price >= 0
        and (max_quantity is null or max_quantity >= 0)
    )
);

create index if not exists ix_commercial_plan_addon_pricing_pricing
    on commercial_plan_addon_pricing (pricing_id);

create index if not exists ix_commercial_plan_addon_pricing_addon
    on commercial_plan_addon_pricing (addon_offer_id);

create table if not exists commercial_pricing_history (
    id uuid primary key,
    pricing_id uuid not null references commercial_plan_pricing(id),
    published_version_id uuid not null references commercial_plan_versions(id),
    content_hash varchar(128) not null,
    snapshot_json jsonb not null,
    change_summary varchar(1000),
    created_at timestamp with time zone not null,
    created_by uuid,
    version bigint not null default 0
);

create index if not exists ix_commercial_pricing_history_pricing
    on commercial_pricing_history (pricing_id);

create index if not exists ix_commercial_pricing_history_version
    on commercial_pricing_history (published_version_id);

