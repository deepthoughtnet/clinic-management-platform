create table if not exists clinic_profiles (
    id uuid primary key,
    tenant_id uuid not null,
    clinic_name varchar(256) not null,
    display_name varchar(256) not null,
    phone varchar(64),
    email varchar(256),
    address_line1 varchar(256) not null,
    address_line2 varchar(256),
    city varchar(128),
    state varchar(128),
    country varchar(128),
    postal_code varchar(32),
    registration_number varchar(128),
    gst_number varchar(128),
    logo_document_id uuid,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_clinic_profiles_tenant unique (tenant_id)
);

create index if not exists ix_clinic_profiles_tenant on clinic_profiles (tenant_id);
