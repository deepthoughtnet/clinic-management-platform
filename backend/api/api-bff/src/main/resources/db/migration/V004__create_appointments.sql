create table if not exists doctor_availability (
    id uuid primary key,
    tenant_id uuid not null,
    doctor_user_id uuid not null,
    day_of_week varchar(16) not null,
    start_time time not null,
    end_time time not null,
    consultation_duration_minutes integer not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_doctor_availability_slot unique (tenant_id, doctor_user_id, day_of_week, start_time, end_time)
);

create index if not exists ix_doctor_availability_tenant_doctor on doctor_availability (tenant_id, doctor_user_id);
create index if not exists ix_doctor_availability_tenant_day on doctor_availability (tenant_id, day_of_week);

create table if not exists appointments (
    id uuid primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    doctor_user_id uuid not null,
    appointment_date date not null,
    appointment_time time,
    token_number integer,
    reason varchar(512),
    type varchar(24) not null,
    status varchar(24) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_appointments_token unique (tenant_id, doctor_user_id, appointment_date, token_number)
);

create index if not exists ix_appointments_tenant_patient on appointments (tenant_id, patient_id);
create index if not exists ix_appointments_tenant_doctor_date on appointments (tenant_id, doctor_user_id, appointment_date);
create index if not exists ix_appointments_tenant_date_status on appointments (tenant_id, appointment_date, status);
