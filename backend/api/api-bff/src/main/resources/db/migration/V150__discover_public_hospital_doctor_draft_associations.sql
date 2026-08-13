create table discover_public_hospital_doctor_draft_associations (
    id uuid primary key,
    public_hospital_reference uuid not null references discover_public_provider_profiles(provider_id),
    public_doctor_reference uuid not null references discover_public_provider_profiles(provider_id),
    source_system varchar(64) not null,
    source_hospital_reference uuid not null,
    source_doctor_reference uuid not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    row_version bigint not null default 0
);

create unique index uq_discover_public_hospital_doctor_draft_associations_natural_key
    on discover_public_hospital_doctor_draft_associations (source_system, source_hospital_reference, source_doctor_reference);

create index ix_discover_public_hospital_doctor_draft_associations_hospital_active
    on discover_public_hospital_doctor_draft_associations (public_hospital_reference, active);

create index ix_discover_public_hospital_doctor_draft_associations_doctor_active
    on discover_public_hospital_doctor_draft_associations (public_doctor_reference, active);
