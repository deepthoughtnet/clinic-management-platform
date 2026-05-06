#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mkdir -p data/postgres data/redis data/minio data/logs

docker compose up -d
docker compose ps