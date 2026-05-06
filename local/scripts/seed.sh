#!/usr/bin/env bash
set -euo pipefail

# Non-destructive helper: prints current demo tenant/user/membership state.
# Expects local Postgres from local/docker-compose.yml.

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5437}"
PGDATABASE="${PGDATABASE:-clinic_management}"
PGUSER="${PGUSER:-clinic}"
PGPASSWORD="${PGPASSWORD:-clinic}"

export PGPASSWORD

echo "Inspecting seeded tenant/users/memberships in ${PGDATABASE} @ ${PGHOST}:${PGPORT}"

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 <<'SQL'
\echo '--- tenants (demo-clinic) ---'
select id, code, name, status
from tenants
where lower(code) = 'demo-clinic';

\echo '--- app_users (demo-clinic) ---'
select u.id, u.email, u.keycloak_sub, u.status
from app_users u
join tenants t on t.id = u.tenant_id
where lower(t.code) = 'demo-clinic'
order by lower(u.email);

\echo '--- tenant_memberships (demo-clinic) ---'
select m.id, u.email, m.role, m.status
from tenant_memberships m
join app_users u on u.id = m.app_user_id and u.tenant_id = m.tenant_id
join tenants t on t.id = m.tenant_id
where lower(t.code) = 'demo-clinic'
order by lower(u.email);
SQL
