create table if not exists doctor_profiles (
    id uuid primary key,
    tenant_id uuid not null,
    doctor_user_id uuid not null,
    mobile varchar(32),
    specialization varchar(128),
    qualification varchar(256),
    registration_number varchar(128),
    consultation_room varchar(128),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint uq_doctor_profiles_tenant_doctor unique (tenant_id, doctor_user_id),
    constraint fk_doctor_profiles_user foreign key (doctor_user_id) references app_users(id)
);

create index if not exists ix_doctor_profiles_tenant_doctor on doctor_profiles (tenant_id, doctor_user_id);
