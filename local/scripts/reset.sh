#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ "${FORCE_RESET_LOCAL_POSTGRES:-0}" != "1" ]]; then
  echo "Refusing to delete the stable Postgres volume automatically."
  echo "Set FORCE_RESET_LOCAL_POSTGRES=1 only when you intentionally want a destructive local reset."
  exit 1
fi

docker compose down || true

docker volume rm jeevanam_dev_postgres_data || true

rm -rf data/postgres data/redis data/minio data/logs
mkdir -p data/postgres data/redis data/minio data/logs

docker compose up -d
docker compose ps
