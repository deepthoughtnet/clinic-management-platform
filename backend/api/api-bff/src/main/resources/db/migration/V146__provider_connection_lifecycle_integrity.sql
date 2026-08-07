alter table public_clinic_platform_links
    add column if not exists proposed_by varchar(160),
    add column if not exists proposed_at timestamptz,
    add column if not exists verified_by varchar(160),
    add column if not exists verified_at timestamptz,
    add column if not exists activated_by varchar(160),
    add column if not exists activated_at timestamptz,
    add column if not exists suspended_by varchar(160),
    add column if not exists suspended_at timestamptz,
    add column if not exists disconnected_by varchar(160),
    add column if not exists disconnected_at timestamptz,
    add column if not exists capability_reason varchar(512);

alter table public_doctor_practice_platform_links
    add column if not exists proposed_by varchar(160),
    add column if not exists proposed_at timestamptz,
    add column if not exists verified_by varchar(160),
    add column if not exists verified_at timestamptz,
    add column if not exists activated_by varchar(160),
    add column if not exists activated_at timestamptz,
    add column if not exists suspended_by varchar(160),
    add column if not exists suspended_at timestamptz,
    add column if not exists disconnected_by varchar(160),
    add column if not exists disconnected_at timestamptz,
    add column if not exists capability_reason varchar(512);

-- Pre-V146 writers treated APPROVED as active. Only LINKED is a current
-- operational connection. Preserve every historical row while repairing the
-- active selector before installing the canonical uniqueness constraints.
update public_clinic_platform_links
set active = false
where active and link_status <> 'LINKED';

update public_doctor_practice_platform_links
set active = false
where active and link_status <> 'LINKED';

create unique index if not exists uq_public_clinic_platform_links_active_public
    on public_clinic_platform_links (public_clinic_reference)
    where active;

create unique index if not exists uq_public_clinic_platform_links_active_target
    on public_clinic_platform_links (tenant_reference, platform_clinic_reference)
    where active;

create unique index if not exists uq_public_doctor_practice_links_active_public
    on public_doctor_practice_platform_links (public_doctor_reference, public_practice_reference)
    where active;

create unique index if not exists uq_public_doctor_practice_links_active_target
    on public_doctor_practice_platform_links (tenant_reference, platform_clinic_reference, tenant_doctor_user_reference)
    where active;
