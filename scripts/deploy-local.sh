#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_command docker

section "Deploy Local"
printf 'Repository root: %s\n' "${REPO_ROOT}"

section "Stop Stack"
compose --profile api --profile frontend down

section "Build All"
"${REPO_ROOT}/scripts/build-all.sh"

section "Start Stack"
compose --profile api --profile frontend up -d --build

section "Containers"
docker_ps_line

section "Result"
printf 'deploy-local completed successfully.\n'
