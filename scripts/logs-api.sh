#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_command docker

section "API Logs"

find_api_container() {
  local candidates=()
  local name
  while IFS= read -r name; do
    [[ -n "${name}" ]] || continue
    candidates+=("${name}")
  done < <(docker ps --format '{{.Names}}' | awk '
    tolower($0) ~ /^clinic-management-api$/ { print; next }
    tolower($0) ~ /(^|[^a-z])api-bff([^a-z]|$)/ { print; next }
  ')

  if ((${#candidates[@]} > 0)); then
    printf '%s\n' "${candidates[0]}"
    return 0
  fi

  for name in clinic-management-api api-bff; do
    if docker inspect "${name}" >/dev/null 2>&1; then
      printf '%s\n' "${name}"
      return 0
    fi
  done

  return 1
}

api_container="$(find_api_container || true)"
if [[ -z "${api_container}" ]]; then
  printf 'No API container found. Checked running containers and common fallbacks.\n' >&2
  exit 1
fi

printf 'Tailing logs for: %s\n' "${api_container}"
docker logs -f "${api_container}"
