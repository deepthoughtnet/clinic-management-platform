create table if not exists commercial_capabilities (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    description varchar(512),
    status varchar(32) not null,
    display_order integer not null default 0,
    standalone_allowed boolean not null default true,
    addon_allowed boolean not null default true,
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    constraint uq_commercial_capabilities_code unique (code),
    constraint ck_commercial_capabilities_code_upper check (code = upper(code)),
    constraint ck_commercial_capabilities_display_order check (display_order >= 0),
    constraint ck_commercial_capabilities_status check (status in ('ACTIVE', 'INACTIVE', 'RETIRED'))
);

create table if not exists commercial_modules (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    description varchar(512),
    status varchar(32) not null,
    display_order integer not null default 0,
    runtime_module_code varchar(64),
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    constraint uq_commercial_modules_code unique (code),
    constraint ck_commercial_modules_code_upper check (code = upper(code)),
    constraint ck_commercial_modules_display_order check (display_order >= 0),
    constraint ck_commercial_modules_status check (status in ('ACTIVE', 'INACTIVE', 'RETIRED'))
);

create table if not exists commercial_capability_modules (
    capability_id uuid not null references commercial_capabilities(id),
    module_id uuid not null references commercial_modules(id),
    included_by_default boolean not null default false,
    display_order integer not null default 0,
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    primary key (capability_id, module_id),
    constraint ck_commercial_capability_modules_display_order check (display_order >= 0)
);

create table if not exists commercial_features (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    description varchar(512),
    status varchar(32) not null,
    display_order integer not null default 0,
    module_id uuid not null references commercial_modules(id),
    runtime_feature_key varchar(128),
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    constraint uq_commercial_features_code unique (code),
    constraint ck_commercial_features_code_upper check (code = upper(code)),
    constraint ck_commercial_features_display_order check (display_order >= 0),
    constraint ck_commercial_features_status check (status in ('ACTIVE', 'INACTIVE', 'RETIRED'))
);

create table if not exists commercial_limit_definitions (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    description varchar(512),
    unit varchar(64) not null,
    value_type varchar(32) not null,
    aggregation_period varchar(32) not null,
    enforcement_mode varchar(32) not null,
    status varchar(32) not null,
    display_order integer not null default 0,
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    constraint uq_commercial_limit_definitions_code unique (code),
    constraint ck_commercial_limit_definitions_code_upper check (code = upper(code)),
    constraint ck_commercial_limit_definitions_display_order check (display_order >= 0),
    constraint ck_commercial_limit_definitions_value_type check (value_type in ('INTEGER', 'DECIMAL', 'BOOLEAN')),
    constraint ck_commercial_limit_definitions_aggregation_period check (aggregation_period in ('NONE', 'DAILY', 'MONTHLY', 'ANNUAL')),
    constraint ck_commercial_limit_definitions_enforcement_mode check (enforcement_mode in ('INFORMATIONAL', 'SOFT', 'HARD')),
    constraint ck_commercial_limit_definitions_status check (status in ('ACTIVE', 'INACTIVE', 'RETIRED'))
);

create table if not exists commercial_addon_offers (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    description varchar(512),
    status varchar(32) not null,
    addon_type varchar(32) not null,
    display_order integer not null default 0,
    repeatable boolean not null default false,
    created_at timestamp with time zone not null default now(),
    created_by uuid,
    updated_at timestamp with time zone not null default now(),
    updated_by uuid,
    constraint uq_commercial_addon_offers_code unique (code),
    constraint ck_commercial_addon_offers_code_upper check (code = upper(code)),
    constraint ck_commercial_addon_offers_display_order check (display_order >= 0),
    constraint ck_commercial_addon_offers_type check (addon_type in ('CAPABILITY', 'FEATURE', 'LIMIT_PACK', 'SERVICE')),
    constraint ck_commercial_addon_offers_status check (status in ('ACTIVE', 'INACTIVE', 'RETIRED'))
);

create table if not exists commercial_addon_capabilities (
    addon_id uuid not null references commercial_addon_offers(id),
    capability_id uuid not null references commercial_capabilities(id),
    primary key (addon_id, capability_id)
);

create table if not exists commercial_addon_modules (
    addon_id uuid not null references commercial_addon_offers(id),
    module_id uuid not null references commercial_modules(id),
    primary key (addon_id, module_id)
);

create table if not exists commercial_addon_features (
    addon_id uuid not null references commercial_addon_offers(id),
    feature_id uuid not null references commercial_features(id),
    primary key (addon_id, feature_id)
);

create table if not exists commercial_addon_limit_increments (
    addon_id uuid not null references commercial_addon_offers(id),
    limit_definition_id uuid not null references commercial_limit_definitions(id),
    increment_value numeric(18, 4) not null,
    primary key (addon_id, limit_definition_id),
    constraint ck_commercial_addon_limit_increments_value_positive check (increment_value > 0)
);

insert into commercial_capabilities (id, code, name, description, status, display_order, standalone_allowed, addon_allowed, created_at, updated_at)
values
    ('e0b5a6e9-6a8e-4485-a5eb-227f9b496afc', 'HEALTHCARE_CORE', 'Healthcare Core', 'Core clinical workflows and operational access', 'ACTIVE', 10, true, true, now(), now()),
    ('05ad8951-3638-49ec-9b12-407d284747ee', 'LABORATORY', 'Laboratory', 'Laboratory workflows and services', 'ACTIVE', 20, true, true, now(), now()),
    ('ea9eeaee-07e8-49ff-87af-4b46addf5c4b', 'PHARMACY', 'Pharmacy', 'Dispensing, inventory and pharmacy operations', 'ACTIVE', 30, true, true, now(), now()),
    ('9d9b8f20-5167-428a-a5d9-fe882dd22eac', 'VACCINATION', 'Vaccination', 'Vaccination service workflows', 'ACTIVE', 40, true, true, now(), now()),
    ('66303847-a363-471a-941a-423f729f56ee', 'ENGAGE', 'Engage', 'Patient engagement and outreach', 'ACTIVE', 50, true, true, now(), now()),
    ('d4a6ea6b-398c-476e-b0f2-841211fd8ea8', 'AI_CLINICAL', 'AI Clinical', 'Clinical AI assistance and reasoning', 'ACTIVE', 60, true, true, now(), now()),
    ('f2648485-917c-4d9d-b051-c529130fd5ff', 'AI_OPERATIONS', 'AI Operations', 'Operational AI workflows', 'ACTIVE', 70, true, true, now(), now())
on conflict (code) do nothing;

insert into commercial_modules (id, code, name, description, status, display_order, runtime_module_code, created_at, updated_at)
values
    ('5c1e83b3-1336-4129-98bf-4b62edff12e9', 'APPOINTMENTS', 'Appointments', 'Scheduling and queue management', 'ACTIVE', 10, 'APPOINTMENTS', now(), now()),
    ('b9da7a79-9fa7-4bec-ab74-e24e5f9c9c96', 'PATIENTS', 'Patients', 'Patient registry and access', 'ACTIVE', 20, 'PATIENTS', now(), now()),
    ('6b570e0a-e2a1-4d8b-863d-db9d109aa094', 'CONSULTATION', 'Consultation', 'Consultation workspace', 'ACTIVE', 30, 'CONSULTATION', now(), now()),
    ('8026fd0a-3e3d-4dbd-90ad-9ad5488489f4', 'PRESCRIPTION', 'Prescription', 'Prescription capture and dispensing', 'ACTIVE', 40, 'PRESCRIPTION', now(), now()),
    ('66bbe65a-937e-44a5-83a5-2330a88d81ab', 'BILLING', 'Billing', 'Billing and collections', 'ACTIVE', 50, 'BILLING', now(), now()),
    ('b91ce2d2-26ee-4a6f-9e77-dae62d7bc16b', 'VACCINATION', 'Vaccination', 'Vaccination workflows', 'ACTIVE', 60, 'VACCINATION', now(), now()),
    ('d3d8c265-c374-4d63-92be-846c5a37fe76', 'INVENTORY', 'Inventory', 'Inventory and stock workflows', 'ACTIVE', 70, 'INVENTORY', now(), now()),
    ('5667f5e8-6d21-49c3-b767-f0be99db4ef1', 'PHARMACY_POS', 'Pharmacy POS', 'Point-of-sale pharmacy billing', 'ACTIVE', 80, 'PHARMACY_POS', now(), now()),
    ('ed7dc4cf-c36a-456f-8c2c-c11b4a987e64', 'LABORATORY', 'Laboratory', 'Lab order and report workflows', 'ACTIVE', 90, 'LABORATORY', now(), now()),
    ('d30864fd-6543-45ec-9ee2-e14474b7d317', 'REPORTS', 'Reports', 'Operational reporting', 'ACTIVE', 100, 'REPORTS', now(), now()),
    ('2dcc4fb9-c717-4a97-b0a8-76d12a2739e7', 'AI_COPILOT', 'AI Copilot', 'Clinical AI assistance', 'ACTIVE', 110, 'AI_COPILOT', now(), now()),
    ('9387ec7d-103e-45d5-b27c-434810225c49', 'CAREPILOT', 'CarePilot', 'Patient engagement module', 'ACTIVE', 120, 'CAREPILOT', now(), now()),
    ('4ea8161a-043e-4014-ad7f-a8fc5225f55a', 'NOTIFICATIONS', 'Notifications', 'Catalog module for notification-related features', 'ACTIVE', 130, null, now(), now())
on conflict (code) do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 10, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'APPOINTMENTS'
where c.code = 'HEALTHCARE_CORE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 20, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'PATIENTS'
where c.code = 'HEALTHCARE_CORE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 30, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'CONSULTATION'
where c.code = 'HEALTHCARE_CORE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 40, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'PRESCRIPTION'
where c.code = 'HEALTHCARE_CORE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 50, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'BILLING'
where c.code = 'HEALTHCARE_CORE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 60, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'REPORTS'
where c.code = 'HEALTHCARE_CORE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 70, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'NOTIFICATIONS'
where c.code = 'HEALTHCARE_CORE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 10, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'LABORATORY'
where c.code = 'LABORATORY'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 10, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'PHARMACY_POS'
where c.code = 'PHARMACY'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 20, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'INVENTORY'
where c.code = 'PHARMACY'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 10, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'VACCINATION'
where c.code = 'VACCINATION'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 10, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'CAREPILOT'
where c.code = 'ENGAGE'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 10, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'AI_COPILOT'
where c.code = 'AI_CLINICAL'
on conflict do nothing;

insert into commercial_capability_modules (capability_id, module_id, included_by_default, display_order, created_at)
select c.id, m.id, true, 10, now()
from commercial_capabilities c
join commercial_modules m on m.code = 'AI_COPILOT'
where c.code = 'AI_OPERATIONS'
on conflict do nothing;

insert into commercial_features (id, code, name, description, status, display_order, module_id, runtime_feature_key, created_at, updated_at)
values
    ('856db0d8-8c63-4a42-a37f-ee5f6b4ac8b5', 'LAB_BARCODE', 'Lab Barcode', 'Lab barcode scanning support', 'ACTIVE', 10, (select id from commercial_modules where code = 'LABORATORY'), 'lab.barcode', now(), now()),
    ('e39c8391-6eb3-463d-bc45-280f9f4dc5e6', 'LAB_HOME_COLLECTION', 'Lab Home Collection', 'Home collection workflows', 'ACTIVE', 20, (select id from commercial_modules where code = 'LABORATORY'), 'lab.home.collection', now(), now()),
    ('ef7aca53-13d1-4c47-a37a-cf010912f760', 'LAB_VERIFICATION', 'Lab Verification', 'Verification workflows', 'ACTIVE', 30, (select id from commercial_modules where code = 'LABORATORY'), 'lab.verification', now(), now()),
    ('dfce0449-6e1b-4861-8ce7-8072314b110f', 'PHARMACY_PROCUREMENT', 'Pharmacy Procurement', 'Procurement workflows', 'ACTIVE', 10, (select id from commercial_modules where code = 'INVENTORY'), 'pharmacy.procurement', now(), now()),
    ('9f474a05-08d1-4f17-aedd-3bb386b3e99f', 'PHARMACY_RECONCILIATION', 'Pharmacy Reconciliation', 'Reconciliation workflows', 'ACTIVE', 20, (select id from commercial_modules where code = 'INVENTORY'), 'pharmacy.reconciliation', now(), now()),
    ('4c5d4196-2f97-494b-bed5-0bb6b1c4c7d0', 'PHARMACY_POS', 'Pharmacy POS', 'POS sale workflows', 'ACTIVE', 30, (select id from commercial_modules where code = 'PHARMACY_POS'), 'pharmacy.pos', now(), now()),
    ('29e35362-e8a5-489e-a026-127153d897a8', 'CLINICAL_REASONING', 'Clinical Reasoning', 'Clinical reasoning support', 'ACTIVE', 10, (select id from commercial_modules where code = 'AI_COPILOT'), 'clinical.reasoning', now(), now()),
    ('8d3f4551-e695-42d6-970e-39e6b186d245', 'REPORT_OCR', 'Report OCR', 'Document OCR extraction', 'ACTIVE', 10, (select id from commercial_modules where code = 'REPORTS'), 'report.ocr', now(), now()),
    ('f65fae47-f00e-4bbc-9792-93b94842bc0c', 'AIVA_VOICE', 'Aiva Voice', 'Voice assistant runtime feature', 'ACTIVE', 20, (select id from commercial_modules where code = 'AI_COPILOT'), 'aiva.voice', now(), now()),
    ('e20057bb-c081-40d5-a42d-152509886a3e', 'ENGAGE_CAMPAIGNS', 'Engage Campaigns', 'Campaign orchestration', 'ACTIVE', 10, (select id from commercial_modules where code = 'CAREPILOT'), 'engage.campaigns', now(), now())
on conflict (code) do nothing;

insert into commercial_limit_definitions (id, code, name, description, unit, value_type, aggregation_period, enforcement_mode, status, display_order, created_at, updated_at)
values
    ('2bfd803f-4c6e-4bd3-9164-dad755137c3f', 'MAX_DOCTORS', 'Max Doctors', 'Maximum doctors per tenant', 'doctors', 'INTEGER', 'NONE', 'INFORMATIONAL', 'ACTIVE', 10, now(), now()),
    ('f7953ee1-fecc-4eef-b192-8379b197afb0', 'MAX_USERS', 'Max Users', 'Maximum user count per tenant', 'users', 'INTEGER', 'NONE', 'INFORMATIONAL', 'ACTIVE', 20, now(), now()),
    ('886f79f0-06db-46a8-ad3e-6ad35cf1fc77', 'MAX_BRANCHES', 'Max Branches', 'Maximum branches per tenant', 'branches', 'INTEGER', 'NONE', 'INFORMATIONAL', 'ACTIVE', 30, now(), now()),
    ('ae5dd05f-28b9-4c88-9836-633ebd65ff9b', 'MAX_PATIENTS', 'Max Patients', 'Maximum patients per tenant', 'patients', 'INTEGER', 'NONE', 'INFORMATIONAL', 'ACTIVE', 40, now(), now()),
    ('17bc0082-78ae-4f27-b974-c6bcc439a3c4', 'STORAGE_GB', 'Storage GB', 'Storage allotment in GB', 'gb', 'DECIMAL', 'MONTHLY', 'INFORMATIONAL', 'ACTIVE', 50, now(), now()),
    ('52b20b0a-611e-4632-bee9-fabe07713dba', 'AI_REQUESTS_MONTHLY', 'AI Requests Monthly', 'Monthly AI request allowance', 'requests', 'INTEGER', 'MONTHLY', 'SOFT', 'ACTIVE', 60, now(), now()),
    ('6be60d6c-a8a2-40e9-b10f-8883855b3cb3', 'OCR_PAGES_MONTHLY', 'OCR Pages Monthly', 'Monthly OCR page allowance', 'pages', 'INTEGER', 'MONTHLY', 'SOFT', 'ACTIVE', 70, now(), now()),
    ('44245c18-edb1-4552-ba4e-4449ee24ca15', 'VOICE_MINUTES_MONTHLY', 'Voice Minutes Monthly', 'Monthly voice minutes allowance', 'minutes', 'INTEGER', 'MONTHLY', 'SOFT', 'ACTIVE', 80, now(), now()),
    ('3ed72ea3-e881-46fe-9372-92da863f6a37', 'WHATSAPP_MESSAGES_MONTHLY', 'WhatsApp Messages Monthly', 'Monthly WhatsApp message allowance', 'messages', 'INTEGER', 'MONTHLY', 'SOFT', 'ACTIVE', 90, now(), now()),
    ('ea23daad-281a-49fb-96dc-719087d7a196', 'SMS_MESSAGES_MONTHLY', 'SMS Messages Monthly', 'Monthly SMS message allowance', 'messages', 'INTEGER', 'MONTHLY', 'SOFT', 'ACTIVE', 100, now(), now())
on conflict (code) do nothing;

insert into commercial_addon_offers (id, code, name, description, status, addon_type, display_order, repeatable, created_at, updated_at)
values
    ('556db0d8-8c63-4a42-a37f-ee5f6b4ac8b5', 'PHARMACY_ADDON', 'Pharmacy Add-on', 'Adds pharmacy commercial coverage', 'ACTIVE', 'CAPABILITY', 10, false, now(), now()),
    ('4c5d4196-2f97-494b-bed5-0bb6b1c4c7d1', 'LABORATORY_ADDON', 'Laboratory Add-on', 'Adds laboratory commercial coverage', 'ACTIVE', 'CAPABILITY', 20, false, now(), now()),
    ('6be60d6c-a8a2-40e9-b10f-8883855b3cb4', 'CLINICAL_AI_ADDON', 'Clinical AI Add-on', 'Adds clinical AI coverage', 'ACTIVE', 'CAPABILITY', 30, false, now(), now()),
    ('44245c18-edb1-4552-ba4e-4449ee24ca16', 'EXTRA_DOCTOR_PACK', 'Extra Doctor Pack', 'Adds extra doctor capacity', 'ACTIVE', 'LIMIT_PACK', 40, true, now(), now())
on conflict (code) do nothing;

insert into commercial_addon_capabilities (addon_id, capability_id)
select a.id, c.id
from commercial_addon_offers a
join commercial_capabilities c on c.code = 'PHARMACY'
where a.code = 'PHARMACY_ADDON'
on conflict do nothing;

insert into commercial_addon_capabilities (addon_id, capability_id)
select a.id, c.id
from commercial_addon_offers a
join commercial_capabilities c on c.code = 'LABORATORY'
where a.code = 'LABORATORY_ADDON'
on conflict do nothing;

insert into commercial_addon_capabilities (addon_id, capability_id)
select a.id, c.id
from commercial_addon_offers a
join commercial_capabilities c on c.code = 'AI_CLINICAL'
where a.code = 'CLINICAL_AI_ADDON'
on conflict do nothing;

insert into commercial_addon_modules (addon_id, module_id)
select a.id, m.id
from commercial_addon_offers a
join commercial_modules m on m.code in ('INVENTORY', 'PHARMACY_POS')
where a.code = 'PHARMACY_ADDON'
on conflict do nothing;

insert into commercial_addon_modules (addon_id, module_id)
select a.id, m.id
from commercial_addon_offers a
join commercial_modules m on m.code = 'LABORATORY'
where a.code = 'LABORATORY_ADDON'
on conflict do nothing;

insert into commercial_addon_features (addon_id, feature_id)
select a.id, f.id
from commercial_addon_offers a
join commercial_features f on f.code in ('PHARMACY_PROCUREMENT', 'PHARMACY_RECONCILIATION')
where a.code = 'PHARMACY_ADDON'
on conflict do nothing;

insert into commercial_addon_features (addon_id, feature_id)
select a.id, f.id
from commercial_addon_offers a
join commercial_features f on f.code in ('LAB_BARCODE', 'LAB_HOME_COLLECTION', 'LAB_VERIFICATION')
where a.code = 'LABORATORY_ADDON'
on conflict do nothing;

insert into commercial_addon_features (addon_id, feature_id)
select a.id, f.id
from commercial_addon_offers a
join commercial_features f on f.code = 'CLINICAL_REASONING'
where a.code = 'CLINICAL_AI_ADDON'
on conflict do nothing;

insert into commercial_addon_limit_increments (addon_id, limit_definition_id, increment_value)
select a.id, l.id, 10
from commercial_addon_offers a
join commercial_limit_definitions l on l.code = 'MAX_DOCTORS'
where a.code = 'EXTRA_DOCTOR_PACK'
on conflict do nothing;
