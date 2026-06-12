#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_command docker
require_command curl

section "Smoke Test"
printf 'Repository root: %s\n' "${REPO_ROOT}"

status_rows=()
overall_fail=0

add_row() {
  local check_name="$1"
  local result="$2"
  local details="$3"
  status_rows+=("${check_name}|${result}|${details}")
  if [[ "${result}" == "FAIL" ]]; then
    overall_fail=1
  fi
}

check_container() {
  local label="$1"
  local container_name="$2"

  if docker inspect "${container_name}" >/dev/null 2>&1; then
    if container_running "${container_name}"; then
      add_row "${label}" "PASS" "${container_name} is running"
    else
      add_row "${label}" "FAIL" "${container_name} exists but is not running"
    fi
  else
    add_row "${label}" "SKIP" "${container_name} is not configured in this environment"
  fi
}

section "Container Checks"
if docker ps >/dev/null 2>&1; then
  add_row "docker ps" "PASS" "docker client can list running containers"
else
  add_row "docker ps" "FAIL" "docker ps failed"
fi

check_container "API container" "clinic-management-api"
check_container "Frontend container" "clinic-web-admin"
check_container "Public frontend" "clinic-web-public"
check_container "Keycloak container" "clinic-keycloak"
check_container "PostgreSQL container" "clinic-postgres"

section "Health Check"
health_url="http://localhost:8089/actuator/health"
health_tmp="$(mktemp)"
trap 'rm -f "${health_tmp}"' EXIT
if curl -fsS "${health_url}" >"${health_tmp}" 2>/dev/null; then
  health_body="$(tr -d '\n' < "${health_tmp}")"
  add_row "Health endpoint" "PASS" "${health_url} responded: ${health_body}"
else
  add_row "Health endpoint" "FAIL" "${health_url} is unreachable"
fi

section "Summary"
printf '%-24s | %-6s | %s\n' "CHECK" "RESULT" "DETAILS"
printf '%-24s-+-%-6s-+-%s\n' "------------------------" "------" "------------------------------------------------------------"
for row in "${status_rows[@]}"; do
  IFS='|' read -r check_name result details <<<"${row}"
  printf '%-24s | %-6s | %s\n' "${check_name}" "${result}" "${details}"
done

if (( overall_fail != 0 )); then
  printf '\nSmoke test completed with failures.\n' >&2
  exit 1
fi

printf '\nSmoke test completed successfully.\n'
