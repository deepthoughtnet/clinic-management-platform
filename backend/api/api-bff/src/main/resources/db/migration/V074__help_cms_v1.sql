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

create unique index if not exists uq_help_content_section_language_version on help_content (section_id, language_code, version);
create index if not exists ix_help_content_section_language_status on help_content (section_id, language_code, status);

create table if not exists help_attachments (
    id uuid primary key,
    section_id uuid not null references help_sections(id) on delete cascade,
    type varchar(16) not null,
    url text not null,
    display_order integer not null default 0
);

create index if not exists ix_help_attachments_section_order on help_attachments (section_id, display_order);

insert into help_pages (id, module_key, page_key, title, icon, status, version, is_active, created_at, updated_at)
select gen_random_uuid(), 'PHARMACY', 'PHARMACY_INVENTORY', 'Inventory Help', 'inventory', 'PUBLISHED', 1, true, now(), now()
where not exists (select 1 from help_pages where page_key = 'PHARMACY_INVENTORY');

with page_row as (
    select id from help_pages where page_key = 'PHARMACY_INVENTORY' limit 1
),
section_rows as (
    select 'DESCRIPTION' as section_key, 'DESCRIPTION' as section_type, 1 as display_order, true as is_collapsible, 'Inventory manages physical medicine stock, batches, expiry, returns, write-offs and physical count.' as description_text
    union all
    select 'WORKFLOW', 'WORKFLOW', 2, true, 'Medicine Master → Add Batch → Stock Available → Dispensing / POS → Stock Movement → Physical Count → Expiry Review → Returns → Write-Off → Audit'
    union all
    select 'PERMISSIONS', 'PERMISSIONS', 3, true, 'PHARMACY_INVENTORY_MANAGER, PHARMACY_ADMIN, CLINIC_ADMIN'
    union all
    select 'FIELD_TABLE', 'FIELD_TABLE', 4, true, '{"fields":[{"fieldName":"Medicine","required":true,"description":"Medicine master record linked to stock.","example":"Paracetamol","maxLength":60},{"fieldName":"Location","required":true,"description":"Stock location or default pharmacy location.","example":"Main Pharmacy","maxLength":60},{"fieldName":"Batch Number","required":true,"description":"Supplier/manufacturer batch printed on medicine strip.","example":"BATCH-2026-001","maxLength":60},{"fieldName":"Expiry Date","required":true,"description":"Expired medicines cannot be dispensed.","example":"2026-12-31"},{"fieldName":"Quantity","required":true,"description":"Physical stock quantity.","example":"100"},{"fieldName":"Purchase Rate","required":false,"description":"Purchase cost per unit.","example":"12.50"},{"fieldName":"MRP","required":false,"description":"Maximum retail price used for POS and billing.","example":"15.00"},{"fieldName":"Remarks","required":false,"description":"Optional remarks for audit trail.","example":"Received from distributor"}]}'
    union all
    select 'VALIDATION_RULES', 'VALIDATION_RULES', 5, true, '{"rules":[{"field":"Medicine","rule":"Required"},{"field":"Location","rule":"Required"},{"field":"Batch Number","rule":"Required, max 60 characters"},{"field":"Expiry Date","rule":"Required and future date"},{"field":"Quantity","rule":"Required, integer greater than 0"},{"field":"Purchase Rate","rule":"Greater than or equal to 0"},{"field":"MRP","rule":"Greater than or equal to purchase rate"},{"field":"Remarks","rule":"Max 250 characters"},{"field":"Common Errors","rule":"Duplicate batch, past expiry, invalid quantity"}]}'
    union all
    select 'COMMON_ERRORS', 'COMMON_ERRORS', 6, true, '{"items":[{"question":"Why is duplicate batch blocked?","answer":"The same medicine, location and batch combination must be unique."},{"question":"Why is expiry blocked?","answer":"Past or expired batches cannot be stocked for dispensing."},{"question":"Why is quantity rejected?","answer":"Quantity must be a whole number greater than zero."}]}'
    union all
    select 'BEST_PRACTICES', 'BEST_PRACTICES', 7, true, '{"items":[{"question":"Review weekly","answer":"Review inventory batches and low stock alerts every week."},{"question":"Do not dispense expired medicines","answer":"Expired stock should be written off or returned."}]}'
    union all
    select 'FAQ', 'FAQ', 8, true, '{"items":[{"question":"How do I add stock?","answer":"Open Inventory, select Stock tab, and create a batch using the medicine, location, batch number, expiry date and quantity."},{"question":"How do I perform physical count?","answer":"Use the Physical Count tab to compare system quantity with physical quantity and save the adjustment reason."},{"question":"How do I process returns?","answer":"Use the Returns tab for customer return, vendor return, and write-off flows."}]}'
)
insert into help_sections (id, page_id, section_key, section_type, display_order, is_collapsible, is_active, created_at, updated_at)
select gen_random_uuid(), p.id, s.section_key, s.section_type, s.display_order, s.is_collapsible, true, now(), now()
from page_row p
cross join section_rows s
where not exists (
    select 1 from help_sections existing
    where existing.page_id = p.id and existing.section_key = s.section_key
);

insert into help_content (id, section_id, language_code, content_json, version, status, created_at, updated_at)
select gen_random_uuid(), hs.id, 'en',
       case hs.section_key
            when 'DESCRIPTION' then '{"title":"Inventory","description":"Inventory manages physical medicine stock, batches, expiry, returns, write-offs and physical count."}'
            when 'WORKFLOW' then '{"steps":[{"title":"Medicine Master","description":"Create or update the medicine master record."},{"title":"Add Batch","description":"Add a physical stock batch with batch number, expiry date and quantity."},{"title":"Stock Available","description":"Stock becomes available for dispensing and POS."},{"title":"Dispensing / POS","description":"Dispense medicines or sell at POS and create stock movements."},{"title":"Stock Movement","description":"Every stock change is auditable."},{"title":"Physical Count","description":"Compare system quantity with actual counted quantity."},{"title":"Expiry Review","description":"Review batches that are expired or nearing expiry."},{"title":"Returns","description":"Process customer return or vendor return."},{"title":"Write-Off","description":"Write off damaged, missing or expired stock."},{"title":"Audit","description":"Keep an audit trail for every inventory action."}]}' 
            when 'PERMISSIONS' then '{"items":[{"question":"Who can manage inventory?","answer":"PHARMACY_INVENTORY_MANAGER, PHARMACY_ADMIN, and CLINIC_ADMIN."}]}'
            when 'FIELD_TABLE' then '{"fields":[{"fieldName":"Medicine","required":true,"description":"Medicine master record linked to stock.","example":"Paracetamol","maxLength":60},{"fieldName":"Location","required":true,"description":"Stock location or default pharmacy location.","example":"Main Pharmacy","maxLength":60},{"fieldName":"Batch Number","required":true,"description":"Supplier/manufacturer batch printed on medicine strip.","example":"BATCH-2026-001","maxLength":60},{"fieldName":"Expiry Date","required":true,"description":"Expired medicines cannot be dispensed.","example":"2026-12-31"},{"fieldName":"Quantity","required":true,"description":"Physical stock quantity.","example":"100"},{"fieldName":"Purchase Rate","required":false,"description":"Purchase cost per unit.","example":"12.50"},{"fieldName":"MRP","required":false,"description":"Maximum retail price used for POS and billing.","example":"15.00"},{"fieldName":"Remarks","required":false,"description":"Optional remarks for audit trail.","example":"Received from distributor"}]}'
            when 'VALIDATION_RULES' then '{"rules":[{"field":"Medicine","rule":"Required"},{"field":"Location","rule":"Required"},{"field":"Batch Number","rule":"Required, max 60 characters"},{"field":"Expiry Date","rule":"Required and future date"},{"field":"Quantity","rule":"Required, integer greater than 0"},{"field":"Purchase Rate","rule":"Greater than or equal to 0"},{"field":"MRP","rule":"Greater than or equal to purchase rate"},{"field":"Remarks","rule":"Max 250 characters"}]}'
            when 'COMMON_ERRORS' then '{"items":[{"question":"Why is duplicate batch blocked?","answer":"The same medicine, location and batch combination must be unique."},{"question":"Why is expiry blocked?","answer":"Past or expired batches cannot be stocked for dispensing."},{"question":"Why is quantity rejected?","answer":"Quantity must be a whole number greater than zero."}]}'
            when 'BEST_PRACTICES' then '{"items":[{"question":"Review weekly","answer":"Review inventory batches and low stock alerts every week."},{"question":"Do not dispense expired medicines","answer":"Expired stock should be written off or returned."}]}'
            when 'FAQ' then '{"items":[{"question":"How do I add stock?","answer":"Open Inventory, select Stock tab, and create a batch using the medicine, location, batch number, expiry date and quantity."},{"question":"How do I perform physical count?","answer":"Use the Physical Count tab to compare system quantity with physical quantity and save the adjustment reason."},{"question":"How do I process returns?","answer":"Use the Returns tab for customer return, vendor return, and write-off flows."}]}'
            else '{"description":"Inventory help."}'
       end,
       1, 'PUBLISHED', now(), now()
from help_sections hs
join help_pages hp on hp.id = hs.page_id and hp.page_key = 'PHARMACY_INVENTORY'
where not exists (
    select 1 from help_content existing
    where existing.section_id = hs.id and existing.language_code = 'en' and existing.version = 1
);
