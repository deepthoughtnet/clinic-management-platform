create table if not exists public_clinic_platform_links
(
    id                         uuid primary key,
    provider_type              varchar(20)   not null,
    source_system              varchar(40)   not null,
    source_entity_reference    varchar(160)  not null,
    source_revision            bigint        not null,
    source_updated_at          timestamp with time zone,
    tenant_reference           varchar(160),
    platform_clinic_reference  varchar(160),
    public_clinic_reference    varchar(160)  not null,
    link_status                varchar(40)   not null,
    connection_status          varchar(40)   not null,
    match_method               varchar(40)   not null,
    match_confidence           varchar(40),
    booking_capability         varchar(40)   not null,
    availability_state         varchar(40)   not null,
    booking_reference          varchar(120)  not null,
    capability_version         bigint        not null,
    connection_revision        bigint        not null,
    active                     boolean       not null,
    reason                     varchar(512),
    linked_at                  timestamp with time zone,
    unlinked_at                timestamp with time zone,
    projected_at               timestamp with time zone,
    created_at                 timestamp with time zone   not null,
    updated_at                 timestamp with time zone   not null,
    row_version                bigint        not null default 0,
    constraint uq_public_clinic_platform_links_booking_reference unique (booking_reference),
    constraint uq_public_clinic_platform_links_natural_key unique (public_clinic_reference, tenant_reference, platform_clinic_reference)
);

create index if not exists idx_public_clinic_platform_links_source
    on public_clinic_platform_links (source_system, source_entity_reference, source_revision desc);

create index if not exists idx_public_clinic_platform_links_booking
    on public_clinic_platform_links (booking_reference, active);

create table if not exists public_doctor_practice_platform_links
(
    id                               uuid primary key,
    provider_type                    varchar(20)   not null,
    source_system                    varchar(40)   not null,
    source_entity_reference          varchar(160)  not null,
    source_revision                  bigint        not null,
    source_updated_at                timestamp with time zone,
    tenant_reference                 varchar(160),
    platform_clinic_reference        varchar(160),
    public_doctor_reference          varchar(160)  not null,
    public_practice_reference        varchar(160)  not null,
    tenant_doctor_user_reference     varchar(160),
    tenant_doctor_profile_reference  varchar(160),
    link_status                      varchar(40)   not null,
    connection_status                varchar(40)   not null,
    match_method                     varchar(40)   not null,
    match_confidence                 varchar(40),
    booking_capability               varchar(40)   not null,
    availability_state               varchar(40)   not null,
    booking_reference                varchar(120)  not null,
    capability_version               bigint        not null,
    connection_revision              bigint        not null,
    active                           boolean       not null,
    reason                           varchar(512),
    linked_at                        timestamp with time zone,
    unlinked_at                      timestamp with time zone,
    projected_at                     timestamp with time zone,
    created_at                       timestamp with time zone   not null,
    updated_at                       timestamp with time zone   not null,
    row_version                      bigint        not null default 0,
    constraint uq_public_doctor_practice_platform_links_booking_reference unique (booking_reference),
    constraint uq_public_doctor_practice_platform_links_natural_key unique (
        public_doctor_reference,
        public_practice_reference,
        tenant_reference,
        platform_clinic_reference,
        tenant_doctor_user_reference
    )
);

create index if not exists idx_public_doctor_practice_platform_links_source
    on public_doctor_practice_platform_links (source_system, source_entity_reference, source_revision desc);

create index if not exists idx_public_doctor_practice_platform_links_booking
    on public_doctor_practice_platform_links (booking_reference, active);
