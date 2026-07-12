#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${REPO_ROOT}/local/docker-compose.yml}"
SERVICE="${DB_SERVICE:-postgres}"
DB_USER="${DB_USER:-clinic}"
DB_PASSWORD="${DB_PASSWORD:-${PGPASSWORD:-clinic}}"
ALLOW_PRODUCTION_RESTORE="${ALLOW_PRODUCTION_RESTORE:-0}"

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <backup-file> <target-database> [--allow-production]" >&2
  exit 1
fi

BACKUP_FILE="$1"
TARGET_DATABASE="$2"
shift 2

for arg in "$@"; do
  case "${arg}" in
    --allow-production)
      ALLOW_PRODUCTION_RESTORE=1
      ;;
    *)
      echo "Unknown argument: ${arg}" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

if [[ ! -s "${BACKUP_FILE}" ]]; then
  echo "Backup file is empty: ${BACKUP_FILE}" >&2
  exit 1
fi

case "${TARGET_DATABASE}" in
  *prod*|*production*)
    if [[ "${ALLOW_PRODUCTION_RESTORE}" != "1" ]]; then
      echo "Refusing production restore without --allow-production." >&2
      exit 1
    fi
    ;;
esac

printf 'Type RESTORE %s to continue: ' "${TARGET_DATABASE}"
read -r CONFIRMATION
if [[ "${CONFIRMATION}" != "RESTORE ${TARGET_DATABASE}" ]]; then
  echo "Restore cancelled." >&2
  exit 1
fi

CONTAINER_ID="$(docker compose -f "${COMPOSE_FILE}" ps -q "${SERVICE}")"
if [[ -z "${CONTAINER_ID}" ]]; then
  echo "Unable to find container for service: ${SERVICE}" >&2
  exit 1
fi

docker cp "${BACKUP_FILE}" "${CONTAINER_ID}:/tmp/db-restore.dump"
docker compose -f "${COMPOSE_FILE}" exec -T -e PGPASSWORD="${DB_PASSWORD}" "${SERVICE}" \
  pg_restore --clean --if-exists --no-owner --no-privileges -U "${DB_USER}" -d "${TARGET_DATABASE}" /tmp/db-restore.dump
docker compose -f "${COMPOSE_FILE}" exec -T "${SERVICE}" rm -f /tmp/db-restore.dump

echo "Restore completed into ${TARGET_DATABASE} from ${BACKUP_FILE}"
