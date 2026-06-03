alter table if exists tenants
    add column if not exists public_listing_enabled boolean not null default false;

alter table if exists clinic_profiles
    add column if not exists public_listing_enabled boolean not null default false,
    add column if not exists slug varchar(192);

alter table if exists doctor_profiles
    add column if not exists public_listing_enabled boolean not null default false,
    add column if not exists slug varchar(192);

create index if not exists ix_tenants_public_listing_enabled on tenants (public_listing_enabled);
create index if not exists ix_clinic_profiles_public_listing_enabled on clinic_profiles (public_listing_enabled);
create index if not exists ix_clinic_profiles_slug on clinic_profiles (slug);
create index if not exists ix_doctor_profiles_public_listing_enabled on doctor_profiles (public_listing_enabled);
create index if not exists ix_doctor_profiles_slug on doctor_profiles (slug);

update tenants
set public_listing_enabled = true
where lower(code) = 'demo-clinic';

update clinic_profiles
set public_listing_enabled = true,
    slug = coalesce(nullif(slug, ''), 'demo-clinic')
where tenant_id = '11111111-1111-1111-1111-111111111111';

update doctor_profiles
set public_listing_enabled = true,
    slug = coalesce(
        nullif(slug, ''),
        case
            when doctor_user_id = '33333333-3333-3333-3333-333333333333' then 'dr-demo-doctor'
            else 'doctor-' || substr(replace(doctor_user_id::text, '-', ''), 1, 8)
        end
    )
where tenant_id = '11111111-1111-1111-1111-111111111111';
