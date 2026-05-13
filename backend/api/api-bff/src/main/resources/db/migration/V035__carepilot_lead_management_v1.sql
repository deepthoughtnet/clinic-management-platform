create table if not exists carepilot_leads (
    id uuid primary key,
    tenant_id uuid not null,
    first_name varchar(128) not null,
    last_name varchar(128),
    full_name varchar(260),
    phone varchar(64) not null,
    email varchar(256),
    gender varchar(16),
    date_of_birth date,
    source varchar(32) not null,
    source_details varchar(512),
    campaign_id uuid,
    assigned_to_app_user_id uuid,
    status varchar(32) not null,
    priority varchar(16) not null,
    notes text,
    tags text,
    converted_patient_id uuid,
    last_contacted_at timestamp with time zone,
    next_follow_up_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    created_by uuid,
    updated_by uuid,
    version integer not null default 0,
    constraint fk_cp_lead_campaign foreign key (campaign_id) references carepilot_campaigns(id),
    constraint fk_cp_lead_patient foreign key (converted_patient_id) references patients(id)
);

create index if not exists ix_cp_leads_tenant_status on carepilot_leads (tenant_id, status);
create index if not exists ix_cp_leads_tenant_source on carepilot_leads (tenant_id, source);
create index if not exists ix_cp_leads_tenant_followup on carepilot_leads (tenant_id, next_follow_up_at);
create index if not exists ix_cp_leads_tenant_assigned on carepilot_leads (tenant_id, assigned_to_app_user_id);
