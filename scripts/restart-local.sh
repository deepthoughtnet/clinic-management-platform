#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_command docker

section "Restart Local"
printf 'Repository root: %s\n' "${REPO_ROOT}"

section "Restart Stack"
compose --profile api --profile frontend restart

section "Containers"
docker_ps_line

section "Result"
printf 'restart-local completed successfully.\n'
