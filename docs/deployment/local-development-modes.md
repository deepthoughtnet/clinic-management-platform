# Local Development Modes

This repository now supports two local modes.

## Mode 1: Hybrid WSL + Docker (default, recommended for day-to-day coding)

Use this for normal development with IntelliJ + Vite:

1. Start infra/runtime containers:
```bash
docker compose -f local/docker-compose.yml up -d
```
2. Run backend locally:
```bash
mvn -f backend/api/api-bff/pom.xml spring-boot:run
```
3. Run frontend locally:
```bash
cd web-admin && npm run dev
```

Notes:
- Existing localhost ports are unchanged:
  - backend `8089`
  - postgres `5437`
  - redis `6383`
  - keycloak `8182`
  - minio api `9005`
  - minio console `9006`
  - realtime voice `8091`
- This mode keeps the current WSL/IntelliJ workflow intact.

## Mode 2: Optional Full Docker Integration (recommended for E2E and container-network testing)

Use this for production-like integration where API and optional frontend run in containers.

1. Start infra + API container:
```bash
docker compose -f local/docker-compose.yml --env-file local/.env.full-docker --profile api up -d
```

2. Start infra + API + frontend container:
```bash
docker compose -f local/docker-compose.yml --env-file local/.env.full-docker --profile api --profile frontend up -d
```

### Profiles
- `api`: starts `clinic-management-api` container.
- `frontend`: starts `web-admin` container (depends on healthy `clinic-management-api`).

### Networking
- API uses internal service DNS for infra dependencies via Spring `docker` profile.
- Realtime runtime uses `VOICE_AI_ORCHESTRATION_URL=http://clinic-management-api:8089/actuator/health` from `local/.env.full-docker`.
- Optional frontend is served on `http://localhost:5174` and reverse-proxies `/api` to `clinic-management-api:8089` inside Compose.

## Keycloak behavior and safety

- Keycloak container and realm import/volumes are unchanged.
- Browser auth remains on external URL: `http://localhost:8182`.
- Docker-internal admin/service URL is `http://keycloak:8080` in `application-docker.yml`.
- Docker JWT validation uses split config:
  - `issuer-uri`: `http://localhost:8182/realms/clinic-management` (must match browser token `iss`)
  - `jwk-set-uri`: `http://keycloak:8080/realms/clinic-management/protocol/openid-connect/certs` (internal JWK fetch)
- If JWT issuer mismatch appears in your environment, override:
  - `CLINIC_JWT_ISSUER_URI`
  - `CLINIC_JWT_JWK_SET_URI`
  - `CLINIC_KEYCLOAK_SERVER_URL`
  with values matching your token issuer strategy.

## Validation commands

```bash
docker compose -f local/docker-compose.yml config
docker compose -f local/docker-compose.yml --env-file local/.env.full-docker --profile api up -d
docker compose -f local/docker-compose.yml --env-file local/.env.full-docker --profile api --profile frontend ps
curl http://localhost:8089/actuator/health
curl http://localhost:8091/ready
```

## Troubleshooting

- Realtime gateway still `DEGRADED` in full Docker mode:
  - restart runtime after API becomes healthy:
  ```bash
  docker compose -f local/docker-compose.yml --env-file local/.env.full-docker restart realtime-voice-gateway
  ```
- Frontend Docker mode does not reflect code changes:
  - use Mode 1 (`npm run dev`) for iterative coding.
- Keep default workflow unchanged:
  - do not pass `--profile` unless you want full Docker integration mode.
