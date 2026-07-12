# Scripts

Utility scripts for local development, deployment, and diagnostics.

Each script resolves the repository root dynamically, so you can run it from any directory inside or outside the repo.

## Scripts

- `build-all.sh` - builds the backend and both web apps without starting containers.
- `deploy-local.sh` - stops the full local Docker stack, rebuilds the project, and starts the API plus frontend profiles.
- `restart-local.sh` - restarts the full local Docker stack without rebuilding.
- `logs-api.sh` - tails the backend API container logs.
- `logs-careai.sh` - tails logs for AI-related containers and lets you choose when more than one matches.
- `deploy-uat-arogia.sh` - builds and starts the isolated Arogia UAT stack on a VPS.
- `status-uat-arogia.sh` - shows compose and container status for the Arogia UAT stack.
- `logs-uat-api.sh` - tails Arogia UAT API logs.
- `logs-uat-aiva.sh` - tails Arogia UAT AIVA and voice-stack logs.
- `smoke-test.sh` - checks that the key containers are running and that the API health endpoint responds.
- `db-backup.sh` - creates a timestamped custom-format PostgreSQL backup.
- `db-restore.sh` - restores a backup into an explicit target database.
- `db-verify.sh` - checks critical tables, Flyway history, and schema columns.

## Usage

```bash
./scripts/build-all.sh
./scripts/deploy-local.sh
./scripts/restart-local.sh
./scripts/deploy-uat-arogia.sh
./scripts/status-uat-arogia.sh
./scripts/logs-uat-api.sh
./scripts/logs-uat-aiva.sh
./scripts/logs-api.sh
./scripts/logs-careai.sh
./scripts/smoke-test.sh
./scripts/db-backup.sh
./scripts/db-restore.sh backups/postgres/clinic_management_YYYYMMDD_HHMMSS.dump clinic_management_test
./scripts/db-verify.sh
```

## Expected Prerequisites

- Bash
- Docker with the Compose plugin
- Maven
- Node.js and npm
- Access to the local Docker stack defined in `local/docker-compose.yml`
- The `local/.env.full-docker` file for full Docker deployment mode

## Notes

- `deploy-local.sh` and `restart-local.sh` target the full local stack using the `api` and `frontend` profiles.
- `smoke-test.sh` expects the API health endpoint to be available on `http://localhost:8089/actuator/health`.
- If multiple AI-related containers are present, `logs-careai.sh` will prompt for a selection.
