create table if not exists doctor_unavailability (
    id uuid primary key,
    tenant_id uuid not null,
    doctor_user_id uuid not null,
    start_at timestamp with time zone not null,
    end_at timestamp with time zone not null,
    block_type varchar(24) not null,
    reason varchar(512),
    active boolean not null default true,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index if not exists ix_doctor_unavailability_tenant_doctor
    on doctor_unavailability (tenant_id, doctor_user_id);
create index if not exists ix_doctor_unavailability_tenant_start
    on doctor_unavailability (tenant_id, start_at);

create table if not exists appointment_waitlist (
    id uuid primary key,
    tenant_id uuid not null,
    patient_id uuid not null,
    doctor_user_id uuid,
    preferred_date date not null,
    preferred_start_time time,
    preferred_end_time time,
    reason varchar(512),
    notes text,
    status varchar(24) not null,
    booked_appointment_id uuid,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index if not exists ix_appointment_waitlist_tenant_status
    on appointment_waitlist (tenant_id, status);
create index if not exists ix_appointment_waitlist_tenant_doctor_date
    on appointment_waitlist (tenant_id, doctor_user_id, preferred_date);
create index if not exists ix_appointment_waitlist_tenant_patient
    on appointment_waitlist (tenant_id, patient_id);
