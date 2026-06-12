#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_command docker

section "Care AI Logs"

find_careai_containers() {
  docker ps --format '{{.Names}}' | awk '
    {
      name = tolower($0)
      if (name ~ /careai/) {
        print $0
        next
      }
      if (name ~ /whisper/) {
        print $0
        next
      }
      if (name ~ /piper/) {
        print $0
        next
      }
      if ((name ~ /realtime-voice/) || ((name ~ /(^|[^a-z])ai([^a-z]|$)/) && name !~ /api/)) {
        print $0
        next
      }
    }
  ' | sort -u
}

mapfile -t containers < <(find_careai_containers)

if ((${#containers[@]} == 0)); then
  printf 'No AI-related containers found. Checked careai, ai, whisper, and piper patterns.\n' >&2
  exit 1
fi

if ((${#containers[@]} == 1)); then
  printf 'Tailing logs for: %s\n' "${containers[0]}"
  docker logs -f "${containers[0]}"
  exit 0
fi

printf 'Multiple AI-related containers found:\n'
for index in "${!containers[@]}"; do
  printf '  %d) %s\n' "$((index + 1))" "${containers[index]}"
done

if [[ ! -t 0 ]]; then
  printf 'Interactive selection requires a terminal. Re-run from a TTY to choose a container.\n' >&2
  exit 1
fi

while true; do
  printf 'Select a container number: '
  IFS= read -r selection
  if [[ "${selection}" =~ ^[0-9]+$ ]] && (( selection >= 1 && selection <= ${#containers[@]} )); then
    chosen_container="${containers[selection - 1]}"
    break
  fi
  printf 'Invalid selection. Enter a number between 1 and %d.\n' "${#containers[@]}"
done

printf 'Tailing logs for: %s\n' "${chosen_container}"
docker logs -f "${chosen_container}"
