#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Shared helpers keep repo-root resolution and section formatting identical across scripts.
# shellcheck source=./lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_command mvn
require_command npm

section "Build All"
printf 'Repository root: %s\n' "${REPO_ROOT}"

section "Backend"
run_in_repo mvn clean install
printf 'Backend build completed.\n'

section "Web Admin"
run_in_repo bash -lc 'cd web-admin && npm run build'
printf 'web-admin build completed.\n'

section "Web Public"
run_in_repo bash -lc 'cd web-public && npm run build'
printf 'web-public build completed.\n'

section "Result"
printf 'build-all completed successfully.\n'
