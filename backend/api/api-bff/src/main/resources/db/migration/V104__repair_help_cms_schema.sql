create table if not exists help_pages (
    id uuid primary key,
    module_key varchar(64) not null,
    page_key varchar(128) not null,
    title varchar(256) not null,
    icon varchar(64),
    status varchar(16) not null,
    version integer not null default 1,
    is_active boolean not null default true,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table if exists help_pages
    add column if not exists module_key varchar(64);
alter table if exists help_pages
    add column if not exists page_key varchar(128);
alter table if exists help_pages
    add column if not exists title varchar(256);
alter table if exists help_pages
    add column if not exists icon varchar(64);
alter table if exists help_pages
    add column if not exists status varchar(16);
alter table if exists help_pages
    add column if not exists version integer not null default 1;
alter table if exists help_pages
    add column if not exists is_active boolean not null default true;
alter table if exists help_pages
    add column if not exists created_by uuid;
alter table if exists help_pages
    add column if not exists updated_by uuid;
alter table if exists help_pages
    add column if not exists created_at timestamptz not null default now();
alter table if exists help_pages
    add column if not exists updated_at timestamptz not null default now();

create unique index if not exists uq_help_pages_page_key on help_pages (page_key);
create index if not exists ix_help_pages_module_status on help_pages (module_key, status, is_active);

create table if not exists help_sections (
    id uuid primary key,
    page_id uuid not null references help_pages(id) on delete cascade,
    section_key varchar(128) not null,
    section_type varchar(32) not null,
    display_order integer not null default 0,
    is_collapsible boolean not null default true,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table if exists help_sections
    add column if not exists page_id uuid;
alter table if exists help_sections
    add column if not exists section_key varchar(128);
alter table if exists help_sections
    add column if not exists section_type varchar(32);
alter table if exists help_sections
    add column if not exists display_order integer not null default 0;
alter table if exists help_sections
    add column if not exists is_collapsible boolean not null default true;
alter table if exists help_sections
    add column if not exists is_active boolean not null default true;
alter table if exists help_sections
    add column if not exists created_at timestamptz not null default now();
alter table if exists help_sections
    add column if not exists updated_at timestamptz not null default now();

create unique index if not exists uq_help_sections_page_key on help_sections (page_id, section_key);
create index if not exists ix_help_sections_page_order on help_sections (page_id, display_order);

create table if not exists help_content (
    id uuid primary key,
    section_id uuid not null references help_sections(id) on delete cascade,
    language_code varchar(8) not null,
    content_json text not null,
    version integer not null,
    status varchar(16) not null,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table if exists help_content
    add column if not exists section_id uuid;
alter table if exists help_content
    add column if not exists language_code varchar(8);
alter table if exists help_content
    add column if not exists content_json text;
alter table if exists help_content
    add column if not exists version integer not null default 1;
alter table if exists help_content
    add column if not exists status varchar(16);
alter table if exists help_content
    add column if not exists created_by uuid;
alter table if exists help_content
    add column if not exists updated_by uuid;
alter table if exists help_content
    add column if not exists created_at timestamptz not null default now();
alter table if exists help_content
    add column if not exists updated_at timestamptz not null default now();

create unique index if not exists uq_help_content_section_language_version on help_content (section_id, language_code, version);
create index if not exists ix_help_content_section_language_status on help_content (section_id, language_code, status);

create table if not exists help_attachments (
    id uuid primary key,
    section_id uuid not null references help_sections(id) on delete cascade,
    type varchar(16) not null,
    url text not null,
    display_order integer not null default 0
);

alter table if exists help_attachments
    add column if not exists section_id uuid;
alter table if exists help_attachments
    add column if not exists type varchar(16);
alter table if exists help_attachments
    add column if not exists url text;
alter table if exists help_attachments
    add column if not exists display_order integer not null default 0;

create index if not exists ix_help_attachments_section_order on help_attachments (section_id, display_order);
