create table if not exists commercial_plan_templates (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    description varchar(512),
    target_segment varchar(64) not null,
    status varchar(32) not null,
    display_order integer not null default 0,
    current_draft_revision integer not null default 1,
    latest_published_version_number integer,
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    version bigint not null default 0,
    constraint uq_commercial_plan_templates_code unique (code),
    constraint ck_commercial_plan_templates_code_upper check (code = upper(code)),
    constraint ck_commercial_plan_templates_display_order check (display_order >= 0),
    constraint ck_commercial_plan_templates_current_draft_revision check (current_draft_revision >= 1),
    constraint ck_commercial_plan_templates_latest_published_version_number check (latest_published_version_number is null or latest_published_version_number >= 1),
    constraint ck_commercial_plan_templates_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
    constraint ck_commercial_plan_templates_target_segment check (target_segment in ('SOLO', 'SMALL_CLINIC', 'MULTI_DOCTOR_CLINIC', 'SPECIALITY_CLINIC', 'DIAGNOSTIC_CENTER', 'PHARMACY', 'ENTERPRISE', 'CUSTOM'))
);

create table if not exists commercial_plan_drafts (
    id uuid primary key,
    template_id uuid not null unique references commercial_plan_templates(id),
    revision integer not null,
    status varchar(32) not null,
    draft_notes varchar(1000),
    validation_status varchar(32) not null,
    publication_ready boolean not null default false,
    config_json text not null,
    config_hash varchar(128) not null,
    validation_json text not null,
    last_validated_at timestamp with time zone,
    last_validated_by uuid,
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    version bigint not null default 0,
    constraint ck_commercial_plan_drafts_revision check (revision >= 1),
    constraint ck_commercial_plan_drafts_status check (status in ('DRAFT', 'READY_TO_PUBLISH', 'BLOCKED')),
    constraint ck_commercial_plan_drafts_validation_status check (validation_status in ('READY', 'BLOCKED'))
);

create index if not exists ix_commercial_plan_drafts_template_revision
    on commercial_plan_drafts (template_id, revision desc);

create table if not exists commercial_plan_versions (
    id uuid primary key,
    template_id uuid not null references commercial_plan_templates(id),
    version_number integer not null,
    version_label varchar(64) not null,
    status varchar(32) not null,
    published_at timestamp with time zone not null,
    published_by uuid,
    publication_notes varchar(1000),
    source_draft_revision integer not null,
    content_hash varchar(128) not null,
    snapshot_json text not null,
    capability_count integer not null default 0,
    module_count integer not null default 0,
    feature_count integer not null default 0,
    limit_count integer not null default 0,
    addon_count integer not null default 0,
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    version bigint not null default 0,
    constraint uq_commercial_plan_versions_template_number unique (template_id, version_number),
    constraint ck_commercial_plan_versions_version_number check (version_number >= 1),
    constraint ck_commercial_plan_versions_status check (status in ('PUBLISHED', 'RETIRED')),
    constraint ck_commercial_plan_versions_source_draft_revision check (source_draft_revision >= 1),
    constraint ck_commercial_plan_versions_counts check (capability_count >= 0 and module_count >= 0 and feature_count >= 0 and limit_count >= 0 and addon_count >= 0)
);

create index if not exists ix_commercial_plan_versions_template_version
    on commercial_plan_versions (template_id, version_number desc);

