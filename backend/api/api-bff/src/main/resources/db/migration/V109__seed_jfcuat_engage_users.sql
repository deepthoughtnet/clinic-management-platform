-- Idempotent jfcuat Engage bootstrap for UAT/local environments.

with target_tenant as (
    select id
    from tenants
    where lower(code) = 'jfcuat'
    limit 1
),
upsert_users as (
    insert into app_users (
        id,
        tenant_id,
        keycloak_sub,
        email,
        username,
        display_name,
        status,
        created_at,
        updated_at
    )
    select *
    from (
        values
            ('55555555-5555-5555-5555-555555555551'::uuid, (select id from target_tenant), 'nisha.kulkarni@jfcuat.local', 'nisha.kulkarni@jfcuat.local', 'nisha.kulkarni', 'Nisha Kulkarni', 'ACTIVE', now(), now()),
            ('55555555-5555-5555-5555-555555555552'::uuid, (select id from target_tenant), 'arjun.rao@jfcuat.local', 'arjun.rao@jfcuat.local', 'arjun.rao', 'Arjun Rao', 'ACTIVE', now(), now())
    ) as seeded(id, tenant_id, keycloak_sub, email, username, display_name, status, created_at, updated_at)
    where exists (select 1 from target_tenant)
      and not exists (
          select 1
          from app_users u
          where u.tenant_id = seeded.tenant_id
            and lower(u.email) = lower(seeded.email)
      )
    returning id, tenant_id, email
)
update app_users u
set keycloak_sub = seeded.email,
    username = seeded.username,
    display_name = seeded.display_name,
    status = 'ACTIVE',
    updated_at = now()
from (
    values
        ('nisha.kulkarni@jfcuat.local', 'nisha.kulkarni', 'Nisha Kulkarni'),
        ('arjun.rao@jfcuat.local', 'arjun.rao', 'Arjun Rao')
) as seeded(email, username, display_name)
where exists (select 1 from target_tenant)
  and lower(u.email) = lower(seeded.email)
  and u.tenant_id = (select id from target_tenant);

with target_tenant as (
    select id
    from tenants
    where lower(code) = 'jfcuat'
    limit 1
)
insert into tenant_memberships (
    id,
    tenant_id,
    app_user_id,
    role,
    status,
    created_at,
    updated_at
)
select
    case when lower(u.email) = 'nisha.kulkarni@jfcuat.local'
        then '66666666-6666-6666-6666-666666666651'::uuid
        else '66666666-6666-6666-6666-666666666652'::uuid
    end,
    u.tenant_id,
    u.id,
    case when lower(u.email) = 'nisha.kulkarni@jfcuat.local' then 'ENGAGE_MANAGER' else 'ENGAGE_EXECUTIVE' end,
    'ACTIVE',
    now(),
    now()
from app_users u
join target_tenant t on t.id = u.tenant_id
where lower(u.email) in ('nisha.kulkarni@jfcuat.local', 'arjun.rao@jfcuat.local')
  and not exists (
      select 1
      from tenant_memberships tm
      where tm.tenant_id = u.tenant_id
        and tm.app_user_id = u.id
  );
