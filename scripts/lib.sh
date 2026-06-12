#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/local/docker-compose.yml"
ENV_FILE="${REPO_ROOT}/local/.env.full-docker"
SCRIPT_NAME="$(basename "${0}")"

trap 'printf "\n!! %s failed.\n" "${SCRIPT_NAME}" >&2' ERR

section() {
  printf '\n== %s ==\n' "$1"
}

info() {
  printf '%s\n' "$1"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$1" >&2
    exit 127
  fi
}

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

run_in_repo() {
  (cd "${REPO_ROOT}" && "$@")
}

container_status() {
  local name="$1"
  docker inspect -f '{{.State.Status}}' "${name}" 2>/dev/null || true
}

container_running() {
  local name="$1"
  [[ "$(container_status "${name}")" == "running" ]]
}

docker_ps_line() {
  docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}
