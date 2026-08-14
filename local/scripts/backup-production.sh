#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# Jeevanam Production Backup
# PostgreSQL + Keycloak DB + MinIO
# ============================================================

TIMESTAMP="$(date -u +%Y%m%d_%H%M%S)"

POSTGRES_CONTAINER="jeevanam-prod-postgres"
MINIO_CONTAINER="jeevanam-prod-minio"

ENV_FILE="/opt/jeevanam/clinic-management-platform/local/.env.prod-jeevanam"

BACKUP_ROOT="/opt/jeevanam/backups"
PG_ROOT="${BACKUP_ROOT}/postgres"
MINIO_ROOT="${BACKUP_ROOT}/minio"

CLINIC_BACKUP="${PG_ROOT}/clinic_management/clinic_management_${TIMESTAMP}.dump"
KEYCLOAK_BACKUP="${PG_ROOT}/keycloak/keycloak_${TIMESTAMP}.dump"
MINIO_BACKUP_DIR="${MINIO_ROOT}/${TIMESTAMP}"

RETENTION_DAYS=14
LOG_FILE="/var/log/jeevanam-backup.log"

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*" | tee -a "${LOG_FILE}"
}

fail() {
  log "ERROR: $*"
  exit 1
}

mkdir -p \
  "${PG_ROOT}/clinic_management" \
  "${PG_ROOT}/keycloak" \
  "${MINIO_ROOT}"

log "============================================================"
log "Starting Jeevanam production backup: ${TIMESTAMP}"

# ------------------------------------------------------------
# Preconditions
# ------------------------------------------------------------

docker inspect "${POSTGRES_CONTAINER}" >/dev/null 2>&1 \
  || fail "Postgres container not found: ${POSTGRES_CONTAINER}"

docker inspect "${MINIO_CONTAINER}" >/dev/null 2>&1 \
  || fail "MinIO container not found: ${MINIO_CONTAINER}"

[[ -f "${ENV_FILE}" ]] \
  || fail "Production environment file missing: ${ENV_FILE}"

# ------------------------------------------------------------
# PostgreSQL - clinic_management
# ------------------------------------------------------------

log "Backing up clinic_management database..."

docker exec "${POSTGRES_CONTAINER}" \
  pg_dump \
    -Fc \
    -U jeevanam_user \
    -d clinic_management \
  > "${CLINIC_BACKUP}"

[[ -s "${CLINIC_BACKUP}" ]] \
  || fail "clinic_management backup is empty"

pg_restore -l "${CLINIC_BACKUP}" >/dev/null 2>&1 \
  || fail "clinic_management backup failed pg_restore validation"

log "clinic_management backup created: ${CLINIC_BACKUP}"

# ------------------------------------------------------------
# PostgreSQL - Keycloak
# ------------------------------------------------------------

log "Backing up keycloak database..."

docker exec "${POSTGRES_CONTAINER}" \
  pg_dump \
    -Fc \
    -U jeevanam_user \
    -d keycloak \
  > "${KEYCLOAK_BACKUP}"

[[ -s "${KEYCLOAK_BACKUP}" ]] \
  || fail "keycloak backup is empty"

pg_restore -l "${KEYCLOAK_BACKUP}" >/dev/null 2>&1 \
  || fail "keycloak backup failed pg_restore validation"

log "keycloak backup created: ${KEYCLOAK_BACKUP}"

# ------------------------------------------------------------
# MinIO
# ------------------------------------------------------------

log "Backing up MinIO..."

MINIO_NETWORK="$(
  docker inspect "${MINIO_CONTAINER}" \
    --format '{{range $name, $config := .NetworkSettings.Networks}}{{$name}}{{end}}'
)"

[[ -n "${MINIO_NETWORK}" ]] \
  || fail "Unable to determine MinIO Docker network"

mkdir -p "${MINIO_BACKUP_DIR}"

docker run --rm \
  --network "${MINIO_NETWORK}" \
  --env-file "${ENV_FILE}" \
  -e BACKUP_TIMESTAMP="${TIMESTAMP}" \
  -v "${MINIO_ROOT}:/backup" \
  --entrypoint /bin/sh \
  minio/mc:latest \
  -c '
    set -eu

    mc alias set jeevanam \
      http://minio:9000 \
      "$MINIO_ROOT_USER" \
      "$MINIO_ROOT_PASSWORD" >/dev/null

    mkdir -p "/backup/$BACKUP_TIMESTAMP"

    mc ls jeevanam | while read -r line; do
      bucket="$(echo "$line" | awk "{print \$NF}" | sed "s:/*\$::")"

      [ -n "$bucket" ] || continue

      echo "Backing up MinIO bucket: $bucket"

      mkdir -p "/backup/$BACKUP_TIMESTAMP/$bucket"

      mc mirror \
        "jeevanam/$bucket" \
        "/backup/$BACKUP_TIMESTAMP/$bucket"
    done
  '

[[ -d "${MINIO_BACKUP_DIR}" ]] \
  || fail "MinIO backup directory was not created"

log "MinIO backup created: ${MINIO_BACKUP_DIR}"

# ------------------------------------------------------------
# Checksums
# ------------------------------------------------------------

log "Generating checksums..."

sha256sum \
  "${CLINIC_BACKUP}" \
  "${KEYCLOAK_BACKUP}" \
  > "${BACKUP_ROOT}/backup_${TIMESTAMP}.sha256"

# ------------------------------------------------------------
# Retention
# ------------------------------------------------------------

log "Applying ${RETENTION_DAYS}-day local retention..."

find "${PG_ROOT}/clinic_management" \
  -type f \
  -name '*.dump' \
  -mtime +"${RETENTION_DAYS}" \
  -delete

find "${PG_ROOT}/keycloak" \
  -type f \
  -name '*.dump' \
  -mtime +"${RETENTION_DAYS}" \
  -delete

find "${MINIO_ROOT}" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -mtime +"${RETENTION_DAYS}" \
  -exec rm -rf {} +

find "${BACKUP_ROOT}" \
  -maxdepth 1 \
  -type f \
  -name 'backup_*.sha256' \
  -mtime +"${RETENTION_DAYS}" \
  -delete

# ------------------------------------------------------------
# Summary
# ------------------------------------------------------------

log "Backup completed successfully."

du -h "${CLINIC_BACKUP}" "${KEYCLOAK_BACKUP}" \
  | tee -a "${LOG_FILE}"

du -sh "${MINIO_BACKUP_DIR}" \
  | tee -a "${LOG_FILE}"

log "============================================================"