alter table carepilot_campaigns
    add column if not exists submitted_by uuid,
    add column if not exists submitted_at timestamp with time zone,
    add column if not exists reviewed_by uuid,
    add column if not exists reviewed_at timestamp with time zone,
    add column if not exists review_comment text,
    add column if not exists approved_by uuid,
    add column if not exists approved_at timestamp with time zone,
    add column if not exists activation_by uuid,
    add column if not exists activation_at timestamp with time zone,
    add column if not exists approval_invalidated_reason text,
    add column if not exists approved_version integer,
    add column if not exists approved_configuration_hash varchar(128);

create table if not exists carepilot_campaign_approval_history (
    id uuid primary key,
    tenant_id uuid not null,
    campaign_id uuid not null,
    event_type varchar(40) not null,
    from_status varchar(24),
    to_status varchar(24) not null,
    actor_id uuid,
    actor_role varchar(64),
    comment text,
    invalidation_reason text,
    campaign_version integer,
    configuration_hash varchar(128),
    created_at timestamp with time zone not null
);

create index if not exists ix_cp_campaign_approval_history_tenant_campaign on carepilot_campaign_approval_history (tenant_id, campaign_id, created_at);

update carepilot_campaigns
set status = case
    when status = 'INACTIVE' then 'PAUSED'
    when status = 'ARCHIVED' then 'COMPLETED'
    else status
end
where status in ('INACTIVE', 'ARCHIVED');
