create table if not exists carepilot_delivery_events (
    id uuid primary key,
    tenant_id uuid,
    execution_id uuid,
    delivery_attempt_id uuid,
    provider_name varchar(80) not null,
    provider_message_id varchar(180),
    channel_type varchar(24) not null,
    external_status varchar(80),
    internal_status varchar(40) not null,
    event_type varchar(80) not null,
    event_timestamp timestamptz,
    raw_payload_redacted text,
    received_at timestamptz not null,
    created_at timestamptz not null
);

create index if not exists ix_cp_delivery_events_tenant_received
    on carepilot_delivery_events (tenant_id, received_at);

create index if not exists ix_cp_delivery_events_execution
    on carepilot_delivery_events (execution_id, event_timestamp);

create index if not exists ix_cp_delivery_events_provider_msg
    on carepilot_delivery_events (provider_name, provider_message_id);

create unique index if not exists uq_cp_delivery_events_dedupe
    on carepilot_delivery_events (provider_name, provider_message_id, internal_status, event_type, event_timestamp);
