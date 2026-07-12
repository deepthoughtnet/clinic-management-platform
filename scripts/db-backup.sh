#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${REPO_ROOT}/local/docker-compose.yml}"
SERVICE="${DB_SERVICE:-postgres}"
DB_NAME="${DB_NAME:-clinic_management}"
DB_USER="${DB_USER:-clinic}"
DB_PASSWORD="${DB_PASSWORD:-${PGPASSWORD:-clinic}}"
BACKUP_DIR="${BACKUP_DIR:-${REPO_ROOT}/backups/postgres}"
TIMESTAMP="$(date -u +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_FILE:-${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.dump}"

mkdir -p "${BACKUP_DIR}"

docker compose -f "${COMPOSE_FILE}" exec -T -e PGPASSWORD="${DB_PASSWORD}" "${SERVICE}" \
  pg_dump -Fc -U "${DB_USER}" -d "${DB_NAME}" > "${BACKUP_FILE}"

if [[ ! -s "${BACKUP_FILE}" ]]; then
  echo "Backup failed: file is empty: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "${BACKUP_FILE}"
