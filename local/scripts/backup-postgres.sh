#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/backups/postgres"
TIMESTAMP="$(date -u +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/clinic_management_${TIMESTAMP}.dump"

echo "Safety warning: do not run 'docker compose down -v' for local data you want to keep."
echo "Safety warning: do not remove the Postgres Docker volume or local Postgres data directory."

mkdir -p "${BACKUP_DIR}"

docker exec \
  -e PGPASSWORD=clinic \
  clinic-postgres \
  pg_dump -Fc -U clinic -d clinic_management -f "/tmp/$(basename "${BACKUP_FILE}")"

docker cp "clinic-postgres:/tmp/$(basename "${BACKUP_FILE}")" "${BACKUP_FILE}"
docker exec clinic-postgres rm -f "/tmp/$(basename "${BACKUP_FILE}")"

echo "Created backup: ${BACKUP_FILE}"
