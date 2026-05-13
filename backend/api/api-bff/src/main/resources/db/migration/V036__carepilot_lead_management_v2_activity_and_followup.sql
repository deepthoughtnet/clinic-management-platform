alter table carepilot_leads
    add column if not exists booked_appointment_id uuid;

alter table carepilot_leads
    add constraint fk_cp_lead_appointment foreign key (booked_appointment_id) references appointments(id);

create table if not exists carepilot_lead_activities (
    id uuid primary key,
    tenant_id uuid not null,
    lead_id uuid not null,
    activity_type varchar(48) not null,
    title varchar(180) not null,
    description text,
    old_status varchar(32),
    new_status varchar(32),
    related_entity_type varchar(48),
    related_entity_id uuid,
    created_by_app_user_id uuid,
    created_at timestamp with time zone not null,
    constraint fk_cp_lead_activities_lead foreign key (lead_id) references carepilot_leads(id)
);

create index if not exists ix_cp_lead_activities_tenant_lead_created on carepilot_lead_activities (tenant_id, lead_id, created_at);
create index if not exists ix_cp_lead_activities_tenant_type on carepilot_lead_activities (tenant_id, activity_type);
create index if not exists ix_cp_lead_activities_tenant_created on carepilot_lead_activities (tenant_id, created_at);
