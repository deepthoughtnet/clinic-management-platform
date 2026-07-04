create table if not exists patient_clinical_intakes (
    id uuid primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    appointment_id uuid null,
    consultation_id uuid null,
    chief_complaint text null,
    height_cm numeric(10,2) null,
    weight_kg numeric(10,2) null,
    bmi numeric(10,2) null,
    blood_pressure_systolic integer null,
    blood_pressure_diastolic integer null,
    pulse_rate integer null,
    temperature numeric(10,2) null,
    temperature_unit varchar(16) null,
    spo2 integer null,
    respiratory_rate integer null,
    random_blood_sugar numeric(10,2) null,
    pain_score integer null,
    notes text null,
    recorded_by_user_id uuid not null,
    recorded_by_name varchar(255) not null,
    complete boolean not null default false,
    completed_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid null,
    updated_by uuid null,
    version integer not null default 0
);

create index if not exists ix_patient_clinical_intakes_tenant_patient_created
    on patient_clinical_intakes (tenant_id, patient_id, created_at desc);

create index if not exists ix_patient_clinical_intakes_tenant_patient_appointment
    on patient_clinical_intakes (tenant_id, patient_id, appointment_id, created_at desc);

create index if not exists ix_patient_clinical_intakes_tenant_patient_consultation
    on patient_clinical_intakes (tenant_id, patient_id, consultation_id, created_at desc);

create index if not exists ix_patient_clinical_intakes_tenant_completed
    on patient_clinical_intakes (tenant_id, complete, created_at desc);
