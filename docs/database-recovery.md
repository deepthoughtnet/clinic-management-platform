# Database Recovery Runbook

Use this runbook when the local PostgreSQL database appears corrupted, partially migrated, or polluted with unexpected schemas such as `startup_context_<UUID>`.

## 1. Identify the active Docker volume

Do not run `docker compose down -v` unless you intend to destroy local data.

Inspect the volume mapping:

```bash
docker volume ls
docker volume inspect jeevanam_dev_postgres_data
```

If you are using the UAT compose file, inspect `jeevanam_uat_postgres_data` instead.

## 2. List databases and schemas

```bash
docker compose -f local/docker-compose.yml exec postgres psql -U clinic -d clinic_management -c '\l'
docker compose -f local/docker-compose.yml exec postgres psql -U clinic -d clinic_management -c "select schema_name from information_schema.schemata order by schema_name;"
```

## 3. Inspect tenant counts

```bash
docker compose -f local/docker-compose.yml exec postgres psql -U clinic -d clinic_management -c "select count(*) as tenant_count from public.tenants;"
docker compose -f local/docker-compose.yml exec postgres psql -U clinic -d clinic_management -c "select count(*) as app_user_count from public.app_users;"
docker compose -f local/docker-compose.yml exec postgres psql -U clinic -d clinic_management -c "select count(*) as patient_count from public.patients;"
```

## 4. Identify `startup_context_%` schemas

```bash
docker compose -f local/docker-compose.yml exec postgres psql -U clinic -d clinic_management -c "select schema_name from information_schema.schemata where schema_name like 'startup_context_%' order by schema_name;"
```

If any exist in a non-test database, stop and take a backup before making changes.

## 5. Take a backup before any repair

```bash
./scripts/db-backup.sh
```

The backup is a custom-format `pg_dump` archive stored outside the PostgreSQL data volume.

## 6. Restore from `pg_dump`

Restore only into an explicit target database:

```bash
./scripts/db-restore.sh backups/postgres/clinic_management_YYYYMMDD_HHMMSS.dump clinic_management_test
```

The restore script requires explicit confirmation and refuses production restores unless `--allow-production` is supplied.

## 7. Attach the correct external volume

The local compose file uses the stable volume name `jeevanam_dev_postgres_data`.

To attach existing data safely:

```bash
docker compose -f local/docker-compose.yml up -d postgres
docker volume inspect jeevanam_dev_postgres_data
```

Do not rename or recreate the volume unless you are deliberately starting from empty storage.

## 8. Verify Flyway and schema integrity

```bash
./scripts/db-verify.sh
```

This checks:

- critical tables
- `flyway_schema_history`
- tenant counts
- `notification_outbox.next_retry_at`

## 9. Restore test procedure

1. Restore the backup into `clinic_management_test`.
2. Run `./scripts/db-verify.sh` against the test database.
3. Start the application against the restored test database.
4. Confirm the health endpoint reports ready.

## 10. Do not use destructive volume commands

Avoid:

- `docker compose down -v`
- `docker system prune --volumes`

These commands remove data that may still be needed for investigation or recovery.
