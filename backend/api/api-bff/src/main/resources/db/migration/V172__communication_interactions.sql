create table if not exists communication_interactions (
    id uuid primary key,
    provider_call_id varchar(128) not null,
    tenant_id uuid,
    communication_channel_id uuid,
    clinic_id uuid,
    location_id uuid,
    channel_type varchar(24) not null,
    provider varchar(64) not null,
    direction varchar(16) not null,
    destination_reference varchar(64),
    caller_number_masked varchar(32),
    patient_identification_status varchar(32) not null,
    identified_patient_id uuid,
    conversation_party_type varchar(32) not null,
    candidate_count integer not null default 0,
    call_status varchar(32) not null,
    started_at timestamptz not null,
    answered_at timestamptz,
    ended_at timestamptz,
    duration_seconds bigint,
    transcript_present boolean not null default false,
    outcome varchar(64),
    provider_end_cause varchar(160),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_comm_interactions_provider_call unique (provider_call_id)
);

create index if not exists ix_comm_interactions_tenant_started
    on communication_interactions (tenant_id, started_at);
create index if not exists ix_comm_interactions_usage
    on communication_interactions (tenant_id, channel_type, provider, direction, started_at);
create index if not exists ix_comm_interactions_identification
    on communication_interactions (tenant_id, patient_identification_status);
