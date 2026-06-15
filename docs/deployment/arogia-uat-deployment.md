# Arogia UAT Deployment

This deployment path is for the Arogia UAT stack on a VPS under `deploy@vps`.
It keeps the Arogia project isolated from other stacks, including Finance Automation Platform, by using a separate compose project name and separate host ports.

## Recommended Layout

- VPS working directory: `~/apps/arogia`
- Compose project name: `arogia_uat`
- Compose files:
  - `local/docker-compose.yml`
  - `local/docker-compose.uat.yml`
- Environment file:
  - `local/.env.uat-arogia`

## Prerequisites

- Docker with Compose plugin
- Git
- A VPS shell login as `deploy@vps`
- A public IP or DNS name for the VPS

## Clone

```bash
mkdir -p ~/apps
cd ~/apps
git clone <repo-url> arogia
cd arogia
```

## Create UAT Env File

```bash
cp local/.env.uat-arogia.example local/.env.uat-arogia
```

Edit `local/.env.uat-arogia` and fill in:

- `SERVER_IP`
- `CONTAINER_NAME_PREFIX`
- `POSTGRES_PASSWORD`
- `MINIO_ROOT_PASSWORD`
- `KEYCLOAK_ADMIN_PASSWORD`
- `CLINIC_PATIENT_PORTAL_SESSION_SECRET`
- `GEMINI_API_KEY`
- `GROQ_API_KEY`

Also verify the port values match the VPS and any existing services already running there.

## Notes About Keycloak

The repository ships with a local realm export in `local/keycloak/realm-export.json`.
That export contains localhost redirect URIs for development.

Before using the UAT login flow on a real VPS, update the Keycloak client redirect URIs inside the running realm so they include the actual UAT host/IP and port values you chose.
Do not commit the VPS IP back into the repository.

## Deploy

```bash
cd ~/apps/arogia
./scripts/deploy-uat-arogia.sh
```

The deploy script:

- checks that `local/.env.uat-arogia` exists
- builds the API and frontend images
- starts the stack with the `api` and `frontend` profiles
- prints the running containers at the end

## Status

```bash
cd ~/apps/arogia
./scripts/status-uat-arogia.sh
```

## Logs

API logs:

```bash
cd ~/apps/arogia
./scripts/logs-uat-api.sh
```

AIVA-related logs:

```bash
cd ~/apps/arogia
./scripts/logs-uat-aiva.sh
```

## Smoke Tests

Use the host and ports you set in `local/.env.uat-arogia`.

- API health: `http://SERVER_IP:8089/actuator/health`
- Web admin: `http://SERVER_IP:5175`
- Patient portal/public app: `http://SERVER_IP:5176`
- Keycloak: `http://SERVER_IP:8090`

If you expose the AIVA microsite through `WEB_AIVA_PORT`, test that URL too.

## Port Separation

Use unique ports for Arogia so it does not collide with Finance Automation Platform or any other stack on the VPS.

The UAT example uses:

- `API_PORT=8089`
- `WEB_ADMIN_PORT=5175`
- `WEB_PUBLIC_PORT=5176`
- `WEB_AIVA_PORT=5177`
- `POSTGRES_PORT=5437`
- `REDIS_PORT=6380`
- `MINIO_API_PORT=9001`
- `MINIO_CONSOLE_PORT=9002`
- `KEYCLOAK_PORT=8090`

If another stack already owns one of those ports, change the value in `local/.env.uat-arogia` before starting the deployment.
