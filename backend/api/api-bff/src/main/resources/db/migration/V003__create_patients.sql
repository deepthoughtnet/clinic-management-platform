create table if not exists patients (
    id uuid primary key,
    tenant_id uuid not null,
    patient_number varchar(64) not null,
    first_name varchar(128) not null,
    last_name varchar(128) not null,
    gender varchar(16) not null,
    date_of_birth date,
    age_years integer,
    mobile varchar(64) not null,
    email varchar(256),
    address_line1 varchar(256),
    address_line2 varchar(256),
    city varchar(128),
    state varchar(128),
    country varchar(128),
    postal_code varchar(32),
    emergency_contact_name varchar(128),
    emergency_contact_mobile varchar(64),
    blood_group varchar(16),
    allergies varchar(512),
    existing_conditions varchar(512),
    notes text,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_patients_tenant_patient_number unique (tenant_id, patient_number)
);

create index if not exists ix_patients_tenant_patient_number on patients (tenant_id, patient_number);
create index if not exists ix_patients_tenant_mobile on patients (tenant_id, mobile);
create index if not exists ix_patients_tenant_name on patients (tenant_id, last_name, first_name);
create unique index if not exists uq_patients_active_mobile on patients (tenant_id, lower(mobile)) where active;
