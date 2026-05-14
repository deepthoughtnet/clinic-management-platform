create table if not exists carepilot_templates (
    id uuid primary key,
    tenant_id uuid not null,
    name varchar(140) not null,
    description varchar(512),
    template_type varchar(40) not null,
    channel varchar(24) not null,
    category varchar(40) not null,
    subject varchar(300),
    body text not null,
    variables_json text,
    is_active boolean not null default true,
    is_system_template boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid,
    updated_by uuid
);

create index if not exists ix_cp_templates_tenant_type
    on carepilot_templates (tenant_id, template_type);

create index if not exists ix_cp_templates_tenant_category
    on carepilot_templates (tenant_id, category);

create index if not exists ix_cp_templates_tenant_active
    on carepilot_templates (tenant_id, is_active);
