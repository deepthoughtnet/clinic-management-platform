# Flyway Migration Discipline

- Never modify a migration that has already been applied in any shared or production environment.
- For schema or seed changes, add a new migration instead of editing an older one.
- Static seed data must be idempotent.
- Prefer `INSERT ... ON CONFLICT DO NOTHING` for reference data and plan rows.
- Avoid running `flyway repair` casually. Use it only when you understand the checksum/state impact.
- If local startup fails because the database is partially migrated or contains stale seed data, reset the local database instead of patching history.

## Local reset

If the local Postgres volume has partial migration state:

```bash
cd /home/iadmin/code/clinic-management-platform/local
FORCE_RESET_LOCAL_POSTGRES=1 ./scripts/reset.sh
docker compose up -d
```

The local Postgres data now lives in the explicit Docker volume `jeevanam_dev_postgres_data`.
Use the recovery scripts before any reset:

- `./scripts/db-backup.sh`
- `./scripts/db-restore.sh`
- `./scripts/db-verify.sh`

The repository also provides `local/scripts/reset.sh`, which requires `FORCE_RESET_LOCAL_POSTGRES=1` before it will remove the stable volume.
