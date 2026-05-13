create table if not exists carepilot_webinars (
    id uuid primary key,
    tenant_id uuid not null,
    title varchar(180) not null,
    description text,
    webinar_type varchar(40) not null,
    status varchar(24) not null,
    webinar_url varchar(1024),
    organizer_name varchar(128),
    organizer_email varchar(256),
    scheduled_start_at timestamptz not null,
    scheduled_end_at timestamptz not null,
    timezone varchar(64) not null,
    capacity integer,
    registration_enabled boolean not null default true,
    reminder_enabled boolean not null default true,
    followup_enabled boolean not null default true,
    tags text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid,
    updated_by uuid,
    version integer not null default 0
);

create index if not exists ix_cp_webinars_tenant_status on carepilot_webinars (tenant_id, status);
create index if not exists ix_cp_webinars_tenant_type on carepilot_webinars (tenant_id, webinar_type);
create index if not exists ix_cp_webinars_tenant_start on carepilot_webinars (tenant_id, scheduled_start_at);

create table if not exists carepilot_webinar_registrations (
    id uuid primary key,
    tenant_id uuid not null,
    webinar_id uuid not null references carepilot_webinars(id) on delete cascade,
    patient_id uuid references patients(id),
    lead_id uuid references carepilot_leads(id),
    attendee_name varchar(180) not null,
    attendee_email varchar(256),
    attendee_phone varchar(64),
    registration_status varchar(24) not null,
    attended boolean not null default false,
    attended_at timestamptz,
    source varchar(24) not null,
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0
);

create unique index if not exists ux_cp_webreg_tenant_webinar_email on carepilot_webinar_registrations (tenant_id, webinar_id, attendee_email);
create index if not exists ix_cp_webreg_tenant_webinar on carepilot_webinar_registrations (tenant_id, webinar_id);
create index if not exists ix_cp_webreg_tenant_status on carepilot_webinar_registrations (tenant_id, registration_status);
create index if not exists ix_cp_webreg_tenant_created on carepilot_webinar_registrations (tenant_id, created_at);
