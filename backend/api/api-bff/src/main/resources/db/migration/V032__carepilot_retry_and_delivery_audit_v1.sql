alter table carepilot_campaign_executions
    add column if not exists delivery_status varchar(40),
    add column if not exists provider_name varchar(80),
    add column if not exists provider_message_id varchar(180),
    add column if not exists last_attempt_at timestamp with time zone,
    add column if not exists failure_reason text;

create table if not exists carepilot_delivery_attempts (
    id uuid primary key,
    tenant_id uuid not null,
    execution_id uuid not null,
    attempt_number integer not null,
    provider_name varchar(80),
    channel_type varchar(24) not null,
    delivery_status varchar(40) not null,
    error_code varchar(80),
    error_message text,
    attempted_at timestamp with time zone not null,
    constraint fk_cp_attempt_execution foreign key (execution_id) references carepilot_campaign_executions(id)
);

create index if not exists ix_cp_attempt_exec_attempt on carepilot_delivery_attempts (execution_id, attempt_number);
create index if not exists ix_cp_attempt_tenant_attempted on carepilot_delivery_attempts (tenant_id, attempted_at);
create index if not exists ix_cp_exec_tenant_retry on carepilot_campaign_executions (tenant_id, status, next_attempt_at);
