#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/backups/postgres"

mkdir -p "${BACKUP_DIR}"

echo "Safety warning: keep backups before risky local DB operations."
echo "Avoid 'docker compose down -v' and avoid removing the Postgres Docker volume."

find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'clinic_management_*.dump' -printf '%TY-%Tm-%Td %TH:%TM:%TS %p\n' | sort
