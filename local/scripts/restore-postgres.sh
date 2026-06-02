#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/backups/postgres"

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <backup-file-or-name>"
  exit 1
fi

INPUT_PATH="$1"
if [[ -f "${INPUT_PATH}" ]]; then
  BACKUP_FILE="${INPUT_PATH}"
else
  BACKUP_FILE="${BACKUP_DIR}/${INPUT_PATH}"
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "Backup file not found: ${BACKUP_FILE}"
  exit 1
fi

echo "Safety warning: this will overwrite objects in the local clinic_management database."
echo "Safety warning: do not run 'docker compose down -v' if you need to preserve local Postgres data."
echo "Safety warning: do not remove the Postgres Docker volume."
echo "Restore source: ${BACKUP_FILE}"
printf "Type RESTORE to continue: "
read -r CONFIRMATION

if [[ "${CONFIRMATION}" != "RESTORE" ]]; then
  echo "Restore cancelled."
  exit 1
fi

docker cp "${BACKUP_FILE}" "clinic-postgres:/tmp/restore.dump"
docker exec \
  -e PGPASSWORD=clinic \
  clinic-postgres \
  pg_restore --clean --if-exists -U clinic -d clinic_management /tmp/restore.dump
docker exec clinic-postgres rm -f /tmp/restore.dump

echo "Restore completed from: ${BACKUP_FILE}"
