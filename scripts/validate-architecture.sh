#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
api_catalog_dir="$root_dir/backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog"

if find "$api_catalog_dir" -type f \( -name '*Entity.java' -o -name '*Repository.java' \) | grep -q .; then
  echo "Forbidden Commercial Catalog persistence files found under api-bff." >&2
  find "$api_catalog_dir" -type f \( -name '*Entity.java' -o -name '*Repository.java' \) >&2
  exit 1
fi

if find "$api_catalog_dir" -maxdepth 1 -type d \( -name db -o -name entity -o -name repository -o -name persistence \) | grep -q .; then
  echo "Forbidden Commercial Catalog persistence package directory found under api-bff." >&2
  find "$api_catalog_dir" -maxdepth 1 -type d \( -name db -o -name entity -o -name repository -o -name persistence \) >&2
  exit 1
fi

if grep -R -n "package com\.deepthoughtnet\.clinic\.api\.platform\.commercialcatalog\.db" "$api_catalog_dir" >/dev/null 2>&1; then
  echo "Forbidden Commercial Catalog db package declaration found under api-bff." >&2
  grep -R -n "package com\.deepthoughtnet\.clinic\.api\.platform\.commercialcatalog\.db" "$api_catalog_dir" >&2
  exit 1
fi

echo "Architecture validation passed."
