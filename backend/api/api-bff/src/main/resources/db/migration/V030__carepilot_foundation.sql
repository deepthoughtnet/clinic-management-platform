create table if not exists carepilot_campaigns (
    id uuid primary key,
    tenant_id uuid not null,
    name varchar(140) not null,
    campaign_type varchar(40) not null,
    status varchar(24) not null,
    trigger_type varchar(24) not null,
    audience_type varchar(24) not null,
    template_id uuid,
    is_active boolean not null default false,
    notes text,
    created_by uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version integer not null default 0
);

create index if not exists ix_cp_campaigns_tenant_status on carepilot_campaigns (tenant_id, status);
create index if not exists ix_cp_campaigns_tenant_created on carepilot_campaigns (tenant_id, created_at);

create table if not exists carepilot_campaign_templates (
    id uuid primary key,
    tenant_id uuid not null,
    name varchar(140) not null,
    channel_type varchar(24) not null,
    subject_line varchar(180),
    body_template text not null,
    is_active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists ix_cp_templates_tenant_created on carepilot_campaign_templates (tenant_id, created_at);

create table if not exists carepilot_campaign_executions (
    id uuid primary key,
    tenant_id uuid not null,
    campaign_id uuid not null,
    template_id uuid,
    channel_type varchar(24) not null,
    recipient_patient_id uuid,
    scheduled_at timestamp with time zone not null,
    status varchar(24) not null,
    attempt_count integer not null default 0,
    last_error text,
    executed_at timestamp with time zone,
    next_attempt_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_cp_execution_campaign foreign key (campaign_id) references carepilot_campaigns(id)
);

create index if not exists ix_cp_exec_tenant_status_scheduled on carepilot_campaign_executions (tenant_id, status, scheduled_at);
create index if not exists ix_cp_exec_tenant_campaign on carepilot_campaign_executions (tenant_id, campaign_id);
create index if not exists ix_cp_exec_scheduled on carepilot_campaign_executions (scheduled_at);
