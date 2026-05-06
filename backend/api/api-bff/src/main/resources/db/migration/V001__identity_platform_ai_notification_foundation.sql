create table if not exists tenant_plans (
    id varchar(32) primary key,
    name varchar(128) not null,
    max_drivers integer,
    max_devices integer,
    max_routes integer,
    features jsonb not null default '{}'::jsonb
);

create table if not exists tenants (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(256) not null,
    plan_id varchar(32) not null,
    status varchar(32) not null,
    module_clinic_automation boolean not null default true,
    module_clinic_generation boolean not null default false,
    module_reconciliation boolean not null default false,
    module_decisioning boolean not null default false,
    module_ai_copilot boolean not null default false,
    module_agent_intake boolean not null default false,
    module_gst_filing boolean not null default false,
    module_doctor_intelligence boolean not null default false,
    module_tele_calling boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint tenants_code_key unique (code),
    constraint fk_tenants_plan_id foreign key (plan_id) references tenant_plans(id)
);

create table if not exists app_users (
    id uuid primary key,
    tenant_id uuid not null,
    keycloak_sub varchar(128) not null,
    email varchar(256),
    display_name varchar(256),
    driver_id uuid,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_users_tenant_sub unique (tenant_id, keycloak_sub),
    constraint fk_app_users_tenant foreign key (tenant_id) references tenants(id)
);

create index if not exists ix_app_users_tenant on app_users (tenant_id);
create index if not exists ix_app_users_sub on app_users (keycloak_sub);
create index if not exists ix_app_users_driver on app_users (tenant_id, driver_id);

create table if not exists tenant_memberships (
    id uuid primary key,
    tenant_id uuid not null,
    app_user_id uuid not null,
    role varchar(64) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_membership_tenant_user unique (tenant_id, app_user_id),
    constraint fk_tenant_memberships_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_tenant_memberships_app_user foreign key (app_user_id) references app_users(id)
);

create index if not exists ix_tenant_memberships_tenant on tenant_memberships (tenant_id);
create index if not exists ix_tenant_memberships_app_user on tenant_memberships (app_user_id);

create table if not exists audit_events (
    id uuid primary key,
    tenant_id uuid not null,
    entity_type varchar(64) not null,
    entity_id uuid not null,
    action varchar(96) not null,
    actor_app_user_id uuid,
    occurred_at timestamptz not null,
    summary text,
    details_json text,
    created_at timestamptz not null
);

create index if not exists ix_audit_events_tenant_entity
    on audit_events (tenant_id, entity_type, entity_id, occurred_at);
create index if not exists ix_audit_events_tenant_action
    on audit_events (tenant_id, action, occurred_at);

create table if not exists notification_outbox (
    id uuid primary key,
    tenant_id uuid not null,
    event_type varchar(128) not null,
    aggregate_type varchar(64) not null,
    aggregate_id uuid not null,
    module varchar(64),
    entity_type varchar(64),
    entity_id uuid,
    deduplication_key varchar(256) not null,
    payload_json text not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz,
    last_error text,
    processed_at timestamptz,
    ignored_at timestamptz,
    ignored_by_app_user_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version integer not null default 0,
    constraint uq_notification_outbox_dedup unique (deduplication_key)
);

create index if not exists ix_notification_outbox_status_next
    on notification_outbox (status, next_attempt_at);
create index if not exists ix_notification_outbox_tenant_status
    on notification_outbox (tenant_id, status);
create index if not exists ix_notification_outbox_aggregate
    on notification_outbox (aggregate_type, aggregate_id);
create index if not exists ix_notification_outbox_tenant_module
    on notification_outbox (tenant_id, module);
create index if not exists ix_notification_outbox_tenant_event_type
    on notification_outbox (tenant_id, event_type);
create index if not exists ix_notification_outbox_entity
    on notification_outbox (entity_type, entity_id);

create table if not exists ai_prompt_templates (
    id uuid primary key,
    product_code varchar(255),
    tenant_id uuid,
    template_code varchar(255) not null,
    version varchar(255) not null,
    task_type varchar(255) not null,
    system_prompt text not null,
    user_prompt_template text not null,
    status varchar(255) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists ix_ai_prompt_templates_code_scope
    on ai_prompt_templates (template_code, tenant_id, product_code, version);
create index if not exists ix_ai_prompt_templates_code_status
    on ai_prompt_templates (template_code, status, updated_at);

create table if not exists ai_request_audit (
    id uuid primary key,
    product_code varchar(255) not null,
    tenant_id uuid not null,
    actor_app_user_id uuid,
    use_case_code varchar(255),
    task_type varchar(255) not null,
    prompt_template_code varchar(255),
    prompt_template_version varchar(255),
    provider varchar(255),
    model varchar(255),
    request_hash varchar(255),
    input_summary text,
    output_summary text,
    status varchar(255) not null,
    confidence numeric(19,4),
    latency_ms bigint,
    input_tokens bigint,
    output_tokens bigint,
    total_tokens bigint,
    estimated_cost numeric(19,4),
    fallback_used boolean not null default false,
    error_message text,
    correlation_id varchar(255),
    created_at timestamptz not null
);

create index if not exists ix_ai_request_audit_product on ai_request_audit (product_code);
create index if not exists ix_ai_request_audit_tenant on ai_request_audit (tenant_id);
create index if not exists ix_ai_request_audit_product_tenant on ai_request_audit (product_code, tenant_id);
create index if not exists ix_ai_request_audit_tenant_task on ai_request_audit (tenant_id, task_type);
create index if not exists ix_ai_request_audit_tenant_created on ai_request_audit (tenant_id, created_at);
create index if not exists ix_ai_request_audit_tenant_status on ai_request_audit (tenant_id, status);
create index if not exists ix_ai_request_audit_correlation on ai_request_audit (correlation_id);

create table if not exists agent_execution_log (
    id uuid primary key,
    tenant_id uuid not null,
    agent_type varchar(255) not null,
    entity_id uuid,
    suggestion_json text,
    status varchar(255) not null,
    executed_by uuid,
    created_at timestamptz not null
);

create index if not exists ix_agent_execution_log_tenant on agent_execution_log (tenant_id);
create index if not exists ix_agent_execution_log_tenant_agent on agent_execution_log (tenant_id, agent_type);
create index if not exists ix_agent_execution_log_tenant_entity on agent_execution_log (tenant_id, entity_id);
create index if not exists ix_agent_execution_log_tenant_status on agent_execution_log (tenant_id, status);
create index if not exists ix_agent_execution_log_created_at on agent_execution_log (created_at);

insert into tenant_plans (id, name, max_drivers, max_devices, max_routes, features)
values
    ('TRIAL', 'Trial', 10, 25, 20, '{}'::jsonb),
    ('BASIC', 'Basic', 50, 100, 100, '{}'::jsonb),
    ('PRO', 'Pro', 250, 500, 500, '{}'::jsonb),
    ('ENTERPRISE', 'Enterprise', null, null, null, '{}'::jsonb)
on conflict (id) do update set
    name = excluded.name,
    max_drivers = excluded.max_drivers,
    max_devices = excluded.max_devices,
    max_routes = excluded.max_routes,
    features = excluded.features;
