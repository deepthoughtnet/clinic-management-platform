create table if not exists vaccines (
    id uuid primary key,
    tenant_id uuid not null,
    vaccine_name varchar(256) not null,
    description text null,
    age_group varchar(128) null,
    recommended_gap_days integer null,
    default_price numeric(18,2) null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index if not exists uq_vaccines_tenant_name on vaccines (tenant_id, vaccine_name);
create index if not exists ix_vaccines_tenant_active on vaccines (tenant_id, active);

create table if not exists patient_vaccinations (
    id uuid primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    vaccine_id uuid null references vaccines(id) on delete set null,
    vaccine_name_snapshot varchar(256) not null,
    dose_number integer null,
    given_date date not null,
    next_due_date date null,
    batch_number varchar(128) null,
    notes text null,
    administered_by_user_id uuid null,
    created_at timestamptz not null
);

create index if not exists ix_patient_vaccinations_tenant_patient on patient_vaccinations (tenant_id, patient_id);
create index if not exists ix_patient_vaccinations_tenant_due on patient_vaccinations (tenant_id, next_due_date);
