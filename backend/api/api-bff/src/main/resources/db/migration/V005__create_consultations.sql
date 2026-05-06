create table if not exists consultations (
    id uuid not null primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    doctor_user_id uuid not null,
    appointment_id uuid,
    chief_complaints text,
    symptoms text,
    diagnosis text,
    clinical_notes text,
    advice text,
    follow_up_date date,
    status varchar(24) not null,
    blood_pressure_systolic integer,
    blood_pressure_diastolic integer,
    pulse_rate integer,
    temperature_value double precision,
    temperature_unit varchar(16),
    weight_kg double precision,
    height_cm double precision,
    spo2 integer,
    completed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists ix_consultations_tenant_patient on consultations (tenant_id, patient_id);
create index if not exists ix_consultations_tenant_doctor on consultations (tenant_id, doctor_user_id);
create index if not exists ix_consultations_tenant_status on consultations (tenant_id, status);
create index if not exists ix_consultations_tenant_appointment on consultations (tenant_id, appointment_id);

create unique index if not exists uq_consultations_tenant_appointment on consultations (tenant_id, appointment_id);
