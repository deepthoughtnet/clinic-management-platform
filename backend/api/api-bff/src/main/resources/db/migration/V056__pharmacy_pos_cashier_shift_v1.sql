create table if not exists pharmacy_cashier_shifts (
    id uuid primary key,
    tenant_id uuid not null,
    cashier_user_id uuid not null,
    opened_at timestamptz not null,
    opened_by uuid not null,
    opening_cash_amount numeric(18,2) not null default 0,
    closed_at timestamptz null,
    closed_by uuid null,
    status varchar(32) not null,
    expected_cash_amount numeric(18,2) not null default 0,
    expected_upi_amount numeric(18,2) not null default 0,
    expected_card_amount numeric(18,2) not null default 0,
    expected_other_amount numeric(18,2) not null default 0,
    actual_cash_amount numeric(18,2) null,
    actual_upi_amount numeric(18,2) null,
    actual_card_amount numeric(18,2) null,
    actual_other_amount numeric(18,2) null,
    variance_amount numeric(18,2) null,
    open_notes text,
    close_notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0
);

create index if not exists ix_pharmacy_cashier_shifts_tenant
    on pharmacy_cashier_shifts (tenant_id);

create index if not exists ix_pharmacy_cashier_shifts_tenant_cashier_status
    on pharmacy_cashier_shifts (tenant_id, cashier_user_id, status);

create index if not exists ix_pharmacy_cashier_shifts_tenant_opened
    on pharmacy_cashier_shifts (tenant_id, opened_at desc);

create unique index if not exists uq_pharmacy_cashier_shifts_open
    on pharmacy_cashier_shifts (tenant_id, cashier_user_id)
    where status = 'OPEN';

alter table pharmacy_sale_payments
    add column if not exists cashier_shift_id uuid;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_name = 'fk_pharmacy_sale_payments_shift'
          and table_name = 'pharmacy_sale_payments'
    ) then
        alter table pharmacy_sale_payments
            add constraint fk_pharmacy_sale_payments_shift
            foreign key (cashier_shift_id) references pharmacy_cashier_shifts(id);
    end if;
end $$;

create index if not exists ix_pharmacy_sale_payments_tenant_shift
    on pharmacy_sale_payments (tenant_id, cashier_shift_id, created_at desc);
