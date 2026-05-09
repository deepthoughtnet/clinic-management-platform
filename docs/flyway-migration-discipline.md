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
docker compose down
sudo rm -rf ./data/postgres
sudo rm -rf ./data/redis
sudo rm -rf ./data/minio
docker compose up -d
docker exec -it clinic-postgres psql -U clinic -d clinic_management -c "CREATE DATABASE keycloak;"
```

The repository also provides `local/scripts/reset.sh`, which performs the same destructive local reset for development use.
