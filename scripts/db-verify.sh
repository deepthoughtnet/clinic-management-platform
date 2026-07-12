#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${REPO_ROOT}/local/docker-compose.yml}"
SERVICE="${DB_SERVICE:-postgres}"
DB_NAME="${DB_NAME:-clinic_management}"
DB_USER="${DB_USER:-clinic}"
DB_PASSWORD="${DB_PASSWORD:-${PGPASSWORD:-clinic}}"
MIN_FLYWAY_VERSION="${MIN_FLYWAY_VERSION:-104}"
MIN_TENANT_COUNT="${MIN_TENANT_COUNT:-1}"

docker compose -f "${COMPOSE_FILE}" exec -T -e PGPASSWORD="${DB_PASSWORD}" "${SERVICE}" \
  psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" <<SQL
\echo 'database=' "${DB_NAME}"
select 'flyway_version=' || coalesce(
    (
        select max(version::int)::text
        from public.flyway_schema_history
        where success = true
          and version ~ '^[0-9]+$'
    ),
    'missing'
);
do \$\$
begin
    if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'tenants') then
        raise exception 'missing table tenants';
    end if;
    if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'app_users') then
        raise exception 'missing table app_users';
    end if;
    if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'patients') then
        raise exception 'missing table patients';
    end if;
    if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'notification_outbox') then
        raise exception 'missing table notification_outbox';
    end if;
    if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'clinical_ai_jobs') then
        raise exception 'missing table clinical_ai_jobs';
    end if;
    if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'help_pages') then
        raise exception 'missing table help_pages';
    end if;
    if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'prescription_safety_reviews') then
        raise exception 'missing table prescription_safety_reviews';
    end if;
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'notification_outbox'
          and column_name = 'next_retry_at'
    ) then
        raise exception 'missing column notification_outbox.next_retry_at';
    end if;
end \$\$;
select 'tenant_count=' || count(*) from public.tenants;
select 'app_user_count=' || count(*) from public.app_users;
select 'patient_count=' || count(*) from public.patients;
do \$\$
begin
    if (
        select count(*)
        from public.tenants
    ) < ${MIN_TENANT_COUNT} then
        raise exception 'tenant count below minimum: %', ${MIN_TENANT_COUNT};
    end if;
    if (
        select coalesce(max(version::int), 0)
        from public.flyway_schema_history
        where success = true
          and version ~ '^[0-9]+$'
    ) < ${MIN_FLYWAY_VERSION} then
        raise exception 'flyway version below minimum: %', ${MIN_FLYWAY_VERSION};
    end if;
end \$\$;
SQL

echo "Verification completed for ${DB_NAME}"
