-- Idempotent demo tenant + user/membership seed for local bootstrap.

-- 1) Tenant
insert into tenants (
    id,
    code,
    name,
    plan_id,
    status,
    module_clinic_automation,
    module_clinic_generation,
    module_reconciliation,
    module_decisioning,
    module_ai_copilot,
    module_agent_intake,
    module_gst_filing,
    module_doctor_intelligence,
    module_tele_calling,
    created_at,
    updated_at
) values (
    '11111111-1111-1111-1111-111111111111',
    'demo-clinic',
    'Demo Clinic',
    'TRIAL',
    'ACTIVE',
    true,
    false,
    false,
    true,
    true,
    true,
    false,
    false,
    false,
    now(),
    now()
);

-- Also normalize by tenant code to keep a single canonical row.
update tenants
set id = '11111111-1111-1111-1111-111111111111',
    name = 'Demo Clinic',
    status = 'ACTIVE',
    updated_at = now()
where lower(code) = 'demo-clinic'
  and id <> '11111111-1111-1111-1111-111111111111';

-- 2) Clinic profile
insert into clinic_profiles (
    id,
    tenant_id,
    clinic_name,
    display_name,
    phone,
    email,
    address_line1,
    address_line2,
    city,
    state,
    country,
    postal_code,
    registration_number,
    gst_number,
    logo_document_id,
    active,
    created_at,
    updated_at
) values (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Demo Clinic',
    'Demo Clinic',
    null,
    'clinic.admin@clinic.local',
    'Demo Clinic Address',
    null,
    'Pune',
    null,
    'India',
    null,
    null,
    null,
    null,
    true,
    now(),
    now()
);

-- 3) App users
-- Note: keycloak_sub is seeded with email placeholder. Runtime provisioning updates
-- it to token sub via email fallback implemented in AppUserProvisionerImpl.
insert into app_users (id, tenant_id, keycloak_sub, email, display_name, driver_id, status, created_at, updated_at) values
('33333333-3333-3333-3333-333333333331', '11111111-1111-1111-1111-111111111111', 'platform.admin@clinic.local', 'platform.admin@clinic.local', 'Platform Admin', null, 'ACTIVE', now(), now()),
('33333333-3333-3333-3333-333333333332', '11111111-1111-1111-1111-111111111111', 'clinic.admin@clinic.local',   'clinic.admin@clinic.local',   'Clinic Admin',   null, 'ACTIVE', now(), now()),
('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'doctor@clinic.local',         'doctor@clinic.local',         'Demo Doctor',    null, 'ACTIVE', now(), now()),
('33333333-3333-3333-3333-333333333334', '11111111-1111-1111-1111-111111111111', 'receptionist@clinic.local',   'receptionist@clinic.local',   'Demo Receptionist', null, 'ACTIVE', now(), now()),
('33333333-3333-3333-3333-333333333335', '11111111-1111-1111-1111-111111111111', 'billing@clinic.local',        'billing@clinic.local',        'Demo Billing',   null, 'ACTIVE', now(), now()),
('33333333-3333-3333-3333-333333333336', '11111111-1111-1111-1111-111111111111', 'auditor@clinic.local',        'auditor@clinic.local',        'Demo Auditor',   null, 'ACTIVE', now(), now())
;

-- Keep existing records aligned by email (if keys were previously created differently).
update app_users
set status = 'ACTIVE',
    updated_at = now()
where tenant_id = '11111111-1111-1111-1111-111111111111'
  and lower(email) in (
    'platform.admin@clinic.local',
    'clinic.admin@clinic.local',
    'doctor@clinic.local',
    'receptionist@clinic.local',
    'billing@clinic.local',
    'auditor@clinic.local'
  );

-- 4) Memberships
insert into tenant_memberships (id, tenant_id, app_user_id, role, status, created_at, updated_at)
select '44444444-4444-4444-4444-444444444441', '11111111-1111-1111-1111-111111111111', u.id, 'PLATFORM_ADMIN', 'ACTIVE', now(), now()
from app_users u
where u.tenant_id = '11111111-1111-1111-1111-111111111111' and lower(u.email) = 'platform.admin@clinic.local'
;

insert into tenant_memberships (id, tenant_id, app_user_id, role, status, created_at, updated_at)
select '44444444-4444-4444-4444-444444444442', '11111111-1111-1111-1111-111111111111', u.id, 'CLINIC_ADMIN', 'ACTIVE', now(), now()
from app_users u
where u.tenant_id = '11111111-1111-1111-1111-111111111111' and lower(u.email) = 'clinic.admin@clinic.local'
;

insert into tenant_memberships (id, tenant_id, app_user_id, role, status, created_at, updated_at)
select '44444444-4444-4444-4444-444444444443', '11111111-1111-1111-1111-111111111111', u.id, 'DOCTOR', 'ACTIVE', now(), now()
from app_users u
where u.tenant_id = '11111111-1111-1111-1111-111111111111' and lower(u.email) = 'doctor@clinic.local'
;

insert into tenant_memberships (id, tenant_id, app_user_id, role, status, created_at, updated_at)
select '44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', u.id, 'RECEPTIONIST', 'ACTIVE', now(), now()
from app_users u
where u.tenant_id = '11111111-1111-1111-1111-111111111111' and lower(u.email) = 'receptionist@clinic.local'
;

insert into tenant_memberships (id, tenant_id, app_user_id, role, status, created_at, updated_at)
select '44444444-4444-4444-4444-444444444445', '11111111-1111-1111-1111-111111111111', u.id, 'BILLING_USER', 'ACTIVE', now(), now()
from app_users u
where u.tenant_id = '11111111-1111-1111-1111-111111111111' and lower(u.email) = 'billing@clinic.local'
;

insert into tenant_memberships (id, tenant_id, app_user_id, role, status, created_at, updated_at)
select '44444444-4444-4444-4444-444444444446', '11111111-1111-1111-1111-111111111111', u.id, 'AUDITOR', 'ACTIVE', now(), now()
from app_users u
where u.tenant_id = '11111111-1111-1111-1111-111111111111' and lower(u.email) = 'auditor@clinic.local'
;
