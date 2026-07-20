alter table carepilot_campaigns
    add column if not exists campaign_reference varchar(32);

alter table carepilot_campaigns
    add column if not exists submitted_by_display_name varchar(256),
    add column if not exists submitted_by_role_label varchar(128),
    add column if not exists submitted_by_employee_code varchar(64),
    add column if not exists submitted_by_username varchar(128),
    add column if not exists reviewed_by_display_name varchar(256),
    add column if not exists reviewed_by_role_label varchar(128),
    add column if not exists reviewed_by_employee_code varchar(64),
    add column if not exists reviewed_by_username varchar(128),
    add column if not exists approved_by_display_name varchar(256),
    add column if not exists approved_by_role_label varchar(128),
    add column if not exists approved_by_employee_code varchar(64),
    add column if not exists approved_by_username varchar(128),
    add column if not exists activation_by_display_name varchar(256),
    add column if not exists activation_by_role_label varchar(128),
    add column if not exists activation_by_employee_code varchar(64),
    add column if not exists activation_by_username varchar(128);

alter table carepilot_campaign_approval_history
    add column if not exists actor_display_name varchar(256),
    add column if not exists actor_role_label varchar(128),
    add column if not exists actor_employee_code varchar(64),
    add column if not exists actor_username varchar(128);

create table if not exists carepilot_campaign_reference_counters (
    tenant_id uuid not null,
    reference_year integer not null,
    next_sequence integer not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint pk_cp_campaign_reference_counters primary key (tenant_id, reference_year)
);

with campaign_sequence as (
    select
        id,
        tenant_id,
        extract(year from created_at at time zone 'UTC')::int as reference_year,
        row_number() over (
            partition by tenant_id, extract(year from created_at at time zone 'UTC')::int
            order by created_at, id
        ) as seq
    from carepilot_campaigns
)
update carepilot_campaigns c
set campaign_reference = format(
        'CAM-%s-%s',
        campaign_sequence.reference_year,
        lpad(campaign_sequence.seq::text, 6, '0')
    )
from campaign_sequence
where c.id = campaign_sequence.id
  and c.campaign_reference is null;

insert into carepilot_campaign_reference_counters (tenant_id, reference_year, next_sequence, created_at, updated_at)
select
    tenant_id,
    reference_year,
    max(seq) + 1,
    now(),
    now()
from (
    select
        tenant_id,
        extract(year from created_at at time zone 'UTC')::int as reference_year,
        right(campaign_reference, 6)::int as seq
    from carepilot_campaigns
    where campaign_reference is not null
) seeded
group by tenant_id, reference_year
on conflict (tenant_id, reference_year) do update
set next_sequence = excluded.next_sequence,
    updated_at = now();

update carepilot_campaigns c
set submitted_by_display_name = coalesce(u.display_name, u.employee_code, u.username, case when c.submitted_by is null then 'System' else 'Former user' end),
    submitted_by_role_label = case upper(coalesce(tm.role, ''))
        when 'ENGAGE_MANAGER' then 'Engage Manager'
        when 'ENGAGE_EXECUTIVE' then 'Engage Executive'
        when 'CLINIC_ADMIN' then 'Clinic Admin'
        when 'AUDITOR' then 'Auditor'
        when 'RECEPTIONIST' then 'Receptionist'
        else 'Unknown role'
    end,
    submitted_by_employee_code = u.employee_code,
    submitted_by_username = u.username
from app_users u
left join tenant_memberships tm on tm.tenant_id = u.tenant_id and tm.app_user_id = u.id
where c.submitted_by = u.id
  and c.tenant_id = u.tenant_id;

update carepilot_campaigns
set submitted_by_display_name = coalesce(submitted_by_display_name, case when submitted_by is null then 'System' else 'Former user' end),
    submitted_by_role_label = coalesce(submitted_by_role_label, 'Unknown role');

update carepilot_campaigns c
set reviewed_by_display_name = coalesce(u.display_name, u.employee_code, u.username, case when c.reviewed_by is null then 'System' else 'Former user' end),
    reviewed_by_role_label = case upper(coalesce(tm.role, ''))
        when 'ENGAGE_MANAGER' then 'Engage Manager'
        when 'ENGAGE_EXECUTIVE' then 'Engage Executive'
        when 'CLINIC_ADMIN' then 'Clinic Admin'
        when 'AUDITOR' then 'Auditor'
        when 'RECEPTIONIST' then 'Receptionist'
        else 'Unknown role'
    end,
    reviewed_by_employee_code = u.employee_code,
    reviewed_by_username = u.username
from app_users u
left join tenant_memberships tm on tm.tenant_id = u.tenant_id and tm.app_user_id = u.id
where c.reviewed_by = u.id
  and c.tenant_id = u.tenant_id;

update carepilot_campaigns
set reviewed_by_display_name = coalesce(reviewed_by_display_name, case when reviewed_by is null then 'System' else 'Former user' end),
    reviewed_by_role_label = coalesce(reviewed_by_role_label, 'Unknown role');

update carepilot_campaigns c
set approved_by_display_name = coalesce(u.display_name, u.employee_code, u.username, case when c.approved_by is null then 'System' else 'Former user' end),
    approved_by_role_label = case upper(coalesce(tm.role, ''))
        when 'ENGAGE_MANAGER' then 'Engage Manager'
        when 'ENGAGE_EXECUTIVE' then 'Engage Executive'
        when 'CLINIC_ADMIN' then 'Clinic Admin'
        when 'AUDITOR' then 'Auditor'
        when 'RECEPTIONIST' then 'Receptionist'
        else 'Unknown role'
    end,
    approved_by_employee_code = u.employee_code,
    approved_by_username = u.username
from app_users u
left join tenant_memberships tm on tm.tenant_id = u.tenant_id and tm.app_user_id = u.id
where c.approved_by = u.id
  and c.tenant_id = u.tenant_id;

update carepilot_campaigns
set approved_by_display_name = coalesce(approved_by_display_name, case when approved_by is null then 'System' else 'Former user' end),
    approved_by_role_label = coalesce(approved_by_role_label, 'Unknown role');

update carepilot_campaigns c
set activation_by_display_name = coalesce(u.display_name, u.employee_code, u.username, case when c.activation_by is null then 'System' else 'Former user' end),
    activation_by_role_label = case upper(coalesce(tm.role, ''))
        when 'ENGAGE_MANAGER' then 'Engage Manager'
        when 'ENGAGE_EXECUTIVE' then 'Engage Executive'
        when 'CLINIC_ADMIN' then 'Clinic Admin'
        when 'AUDITOR' then 'Auditor'
        when 'RECEPTIONIST' then 'Receptionist'
        else 'Unknown role'
    end,
    activation_by_employee_code = u.employee_code,
    activation_by_username = u.username
from app_users u
left join tenant_memberships tm on tm.tenant_id = u.tenant_id and tm.app_user_id = u.id
where c.activation_by = u.id
  and c.tenant_id = u.tenant_id;

update carepilot_campaigns
set activation_by_display_name = coalesce(activation_by_display_name, case when activation_by is null then 'System' else 'Former user' end),
    activation_by_role_label = coalesce(activation_by_role_label, 'Unknown role');

update carepilot_campaign_approval_history h
set actor_display_name = coalesce(u.display_name, u.employee_code, u.username, case when h.actor_id is null then 'System' else 'Former user' end),
    actor_role_label = case upper(coalesce(h.actor_role, tm.role, ''))
        when 'ENGAGE_MANAGER' then 'Engage Manager'
        when 'ENGAGE_EXECUTIVE' then 'Engage Executive'
        when 'CLINIC_ADMIN' then 'Clinic Admin'
        when 'AUDITOR' then 'Auditor'
        when 'RECEPTIONIST' then 'Receptionist'
        else 'Unknown role'
    end,
    actor_employee_code = u.employee_code,
    actor_username = u.username
from app_users u
left join tenant_memberships tm on tm.tenant_id = u.tenant_id and tm.app_user_id = u.id
where h.actor_id = u.id
  and h.tenant_id = u.tenant_id;

update carepilot_campaign_approval_history
set actor_display_name = coalesce(actor_display_name, case when actor_id is null then 'System' else 'Unknown user' end),
    actor_role_label = coalesce(actor_role_label, 'Unknown role');

alter table carepilot_campaigns
    alter column campaign_reference set not null;

create unique index if not exists uq_cp_campaigns_tenant_reference
    on carepilot_campaigns (tenant_id, campaign_reference);
