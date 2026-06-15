#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${REPO_ROOT}/local/.env.uat-arogia"
COMPOSE_BASE="${REPO_ROOT}/local/docker-compose.yml"
COMPOSE_UAT="${REPO_ROOT}/local/docker-compose.uat.yml"
PROJECT_NAME="arogia_uat"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$1" >&2
    exit 127
  fi
}

compose() {
  docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}" -f "${COMPOSE_BASE}" -f "${COMPOSE_UAT}" "$@"
}

show_missing_env_help() {
  printf 'Missing env file: %s\n' "${ENV_FILE}" >&2
  printf 'Create it from the example first:\n' >&2
  printf '  cp local/.env.uat-arogia.example local/.env.uat-arogia\n' >&2
  printf 'Then edit secrets, SERVER_IP, and port assignments.\n' >&2
}

require_command docker

if [[ ! -f "${ENV_FILE}" ]]; then
  show_missing_env_help
  exit 1
fi

printf 'Repository root: %s\n' "${REPO_ROOT}"
printf 'Using env file: %s\n' "${ENV_FILE}"

printf '\n== Compose Status ==\n'
compose --profile api --profile frontend ps

printf '\n== Docker Status ==\n'
docker ps --filter "label=com.docker.compose.project=${PROJECT_NAME}" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
