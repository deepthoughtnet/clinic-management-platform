create table if not exists prescriptions (
    id uuid not null primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    doctor_user_id uuid not null,
    consultation_id uuid not null,
    appointment_id uuid,
    prescription_number varchar(64) not null,
    diagnosis_snapshot text,
    advice text,
    follow_up_date date,
    status varchar(24) not null,
    finalized_at timestamptz,
    printed_at timestamptz,
    sent_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index if not exists uq_prescriptions_tenant_number on prescriptions (tenant_id, prescription_number);
create unique index if not exists uq_prescriptions_tenant_consultation on prescriptions (tenant_id, consultation_id);
create index if not exists ix_prescriptions_tenant_patient on prescriptions (tenant_id, patient_id);
create index if not exists ix_prescriptions_tenant_consultation on prescriptions (tenant_id, consultation_id);
create index if not exists ix_prescriptions_tenant_doctor on prescriptions (tenant_id, doctor_user_id);
create index if not exists ix_prescriptions_tenant_status on prescriptions (tenant_id, status);

create table if not exists prescription_medicines (
    id uuid not null primary key,
    tenant_id uuid not null,
    prescription_id uuid not null,
    medicine_name varchar(256) not null,
    medicine_type varchar(24),
    strength varchar(128),
    dosage varchar(128) not null,
    frequency varchar(64) not null,
    duration varchar(64) not null,
    timing varchar(24),
    instructions text,
    sort_order integer not null
);

create index if not exists ix_prescription_medicines_tenant_prescription on prescription_medicines (tenant_id, prescription_id);
create index if not exists ix_prescription_medicines_tenant_sort on prescription_medicines (tenant_id, prescription_id, sort_order);

create table if not exists prescription_tests (
    id uuid not null primary key,
    tenant_id uuid not null,
    prescription_id uuid not null,
    test_name varchar(256) not null,
    instructions text,
    sort_order integer not null
);

create index if not exists ix_prescription_tests_tenant_prescription on prescription_tests (tenant_id, prescription_id);
create index if not exists ix_prescription_tests_tenant_sort on prescription_tests (tenant_id, prescription_id, sort_order);
