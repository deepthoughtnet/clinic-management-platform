# Jeevanam Production Deployment Runbook

**Document:** `JEEVANAM_PRODUCTION_DEPLOYMENT.md`  
**Baseline Date:** 2026-08-14  
**Platform:** Jeevanam Healthcare Platform  
**Deployment Type:** Production VPS  
**Purpose:** Reproducible deployment, configuration, validation, backup, and recovery baseline for Jeevanam production.

> **Security note**
>
> This document intentionally contains no production passwords, API keys, session secrets, or other credentials.  
> Never commit `local/.env.prod-jeevanam` or real secrets to Git.

---

## 1. Production Target

The production VPS used for Jeevanam has the following baseline:

```text
Hostname: jeevanam.deepthoughtnet.com
Public IP: 160.250.204.124
OS: Ubuntu 24.04 LTS
CPU: 8 vCPU
RAM: ~31 GB
Disk: ~394 GB
Timezone: Asia/Kolkata
```

The initial production release was deployed from:

```text
jeevanam-controlled-access-baseline-2026-08-14
```

The deployed stack contains:

```text
PostgreSQL
Redis
MinIO
Keycloak
Jeevanam API/BFF
Jeevanam Discover
Jeevanam Care
Jeevanam Healthcare / web-admin
AIVA frontend
Whisper STT
Faster-Whisper
Piper TTS
Realtime Voice Gateway
Nginx
Let's Encrypt / Certbot
```

---

## 2. Final Production Domain Architecture

The following public hostnames are used:

```text
https://jeevanam.deepthoughtnet.com
→ Jeevanam Discover

https://care.jeevanam.deepthoughtnet.com
→ Jeevanam Care

https://health.jeevanam.deepthoughtnet.com
→ Jeevanam Healthcare / web-admin

https://api.jeevanam.deepthoughtnet.com
→ API/BFF

https://auth.jeevanam.deepthoughtnet.com
→ Keycloak

https://aiva.jeevanam.deepthoughtnet.com
→ AIVA
```

High-level routing:

```text
Internet
   │
   │ 80 / 443
   ▼
 Nginx
   │
   ├── Discover       → 127.0.0.1:5178
   ├── Care           → 127.0.0.1:5176
   ├── Healthcare     → 127.0.0.1:5175
   ├── API            → 127.0.0.1:8089
   ├── Keycloak       → 127.0.0.1:8090
   ├── AIVA           → 127.0.0.1:5177
   └── Voice Gateway  → 127.0.0.1:8091

Docker internal only:
   PostgreSQL
   Redis
   MinIO
   Whisper
   Faster-Whisper
   Piper
```

### Why this structure

Only Nginx should be Internet-facing. Internal services remain private to the Docker network or bound only to `127.0.0.1`.

This reduces accidental exposure of:

```text
PostgreSQL
Redis
MinIO
Voice processing services
Internal API ports
```

---

## 3. Inspect the VPS Before Deployment

Before installation, verify the machine.

```bash
hostname

cat /etc/os-release

nproc
lscpu

free -h

df -h

ip addr

docker --version
docker compose version

ss -tulpn

ufw status

timedatectl
```

### Purpose

These checks confirm:

- correct server;
- expected OS;
- available CPU/RAM/disk;
- current network listeners;
- Docker state;
- system timezone;
- NTP synchronization.

---

## 4. Install and Validate Docker

Verify Docker:

```bash
docker --version
docker compose version
systemctl is-active docker
```

Run a container smoke test:

```bash
docker run --rm hello-world
```

Expected:

```text
Hello from Docker!
```

Production deployment was validated with:

```text
Docker 29.7.2
Docker Compose v5.4.0
```

---

## 5. Install Required Utilities

```bash
apt update

apt install -y \
  git \
  jq \
  unzip
```

Verify:

```bash
git --version
jq --version
```

These utilities are used for repository access, JSON inspection, Keycloak administration, and deployment diagnostics.

---

## 6. GitHub Access

Repository:

```text
https://github.com/deepthoughtnet/clinic-management-platform.git
```

Verify the configured development remote:

```bash
git remote -v
```

On the VPS, configure SSH access and test:

```bash
ssh -T git@github-jeevanam
```

On first use, verify the GitHub host fingerprint before accepting it.

---

## 7. Production Repository Location

Use:

```text
/opt/jeevanam/clinic-management-platform
```

Example:

```bash
cd /opt/jeevanam/clinic-management-platform
```

Fetch release tags:

```bash
git fetch --tags
```

Verify the deployment tag:

```bash
git tag --list "jeevanam-controlled-access-baseline-2026-08-14"
```

Checkout:

```bash
git checkout jeevanam-controlled-access-baseline-2026-08-14
```

The production deployment was initially pinned to:

```text
da22d1a5
feat: add controlled access for Care and Provider portals
```

### Why deploy a tag

Production should start from a known release baseline rather than an arbitrary `main` commit.

---

## 8. Review Existing Compose Files

Relevant existing files:

```text
local/docker-compose.yml
local/docker-compose.uat.yml
local/.env.uat-arogia.example
```

Compare UAT and local definitions:

```bash
cd /opt/jeevanam/clinic-management-platform

diff -u \
  local/docker-compose.uat.yml \
  local/docker-compose.yml \
  > /tmp/jeevanam-compose-diff.txt || true
```

Inspect:

```bash
sed -n '1,260p' /tmp/jeevanam-compose-diff.txt

sed -n '260,520p' /tmp/jeevanam-compose-diff.txt

sed -n '520,780p' /tmp/jeevanam-compose-diff.txt
```

### Decision

The UAT compose was used as the initial production baseline because it already contained the full service topology.

---

## 9. Create `docker-compose.prod.yml`

From:

```bash
cd /opt/jeevanam/clinic-management-platform/local
```

Create:

```bash
cp docker-compose.uat.yml docker-compose.prod.yml
```

Verify the initial copy:

```bash
sha256sum docker-compose.uat.yml docker-compose.prod.yml
```

Then change production naming:

```bash
sed -i \
  -e 's/jeevanam-uat/jeevanam-prod/g' \
  -e 's/jeevanam_uat_postgres_data/jeevanam_prod_postgres_data/g' \
  docker-compose.prod.yml
```

Verify:

```bash
grep -nE \
'jeevanam-uat|jeevanam_uat|jeevanam-prod|jeevanam_prod' \
  docker-compose.prod.yml
```

Production resources should use names such as:

```text
jeevanam-prod-postgres
jeevanam-prod-keycloak
jeevanam-prod-api
jeevanam-prod-web-admin
jeevanam-prod-web-care
jeevanam-prod-web-discover
```

The production PostgreSQL volume is:

```text
jeevanam_prod_postgres_data
```

---

## 10. Production Port Hardening

Do not expose internal infrastructure to the public network.

Internal-only services:

```text
PostgreSQL
Redis
MinIO
Whisper
Faster-Whisper
Piper
```

Services required by Nginx should bind to localhost only:

```text
127.0.0.1:8089  API
127.0.0.1:8090  Keycloak
127.0.0.1:5175  Healthcare
127.0.0.1:5176  Care
127.0.0.1:5177  AIVA
127.0.0.1:5178  Discover
127.0.0.1:8091  Realtime Voice Gateway
```

Example:

```yaml
ports:
  - "127.0.0.1:${API_PORT:-8089}:8089"
```

Keycloak:

```yaml
ports:
  - "127.0.0.1:${KEYCLOAK_PORT:-8090}:8080"
```

---

## 11. Create the Production Environment File

File:

```text
local/.env.prod-jeevanam
```

Create and secure it:

```bash
cd /opt/jeevanam/clinic-management-platform/local

touch .env.prod-jeevanam
chmod 600 .env.prod-jeevanam
chown root:root .env.prod-jeevanam
```

### Production identity

```dotenv
COMPOSE_PROJECT_NAME=jeevanam_prod
APP_ENV=prod
CONTAINER_NAME_PREFIX=jeevanam-prod

BRANDING_PRODUCT_NAME=Jeevanam
BRANDING_TAGLINE=Intelligent Healthcare Platform
BRANDING_COMPANY_NAME=DeepThoughtNet
BRANDING_AI_PLATFORM_NAME=AIVA

SERVER_IP=160.250.204.124
```

### PostgreSQL

```dotenv
POSTGRES_DB=clinic_management
POSTGRES_USER=jeevanam_user
POSTGRES_PASSWORD=<PRODUCTION_SECRET>
```

### MinIO

```dotenv
MINIO_ROOT_USER=jeevanam_minio
MINIO_ROOT_PASSWORD=<PRODUCTION_SECRET>

CLINIC_MINIO_ACCESS_KEY=jeevanam_minio
CLINIC_MINIO_SECRET_KEY=<SAME_MINIO_SECRET>
```

### Keycloak

```dotenv
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<PRODUCTION_SECRET>

CLINIC_KEYCLOAK_ADMIN_USERNAME=admin
CLINIC_KEYCLOAK_ADMIN_PASSWORD=<SAME_KEYCLOAK_ADMIN_SECRET>
```

### Mail

SMTP is intentionally disabled until configured:

```dotenv
CLINIC_MAIL_ENABLED=false

CLINIC_MAIL_HOST=
CLINIC_MAIL_USERNAME=
CLINIC_MAIL_PASSWORD=
CLINIC_MAIL_FROM_EMAIL=
```

---

## 12. Generate Strong Secrets

Generate credentials directly on the VPS.

Examples:

```bash
openssl rand -base64 36
```

For longer session secrets:

```bash
openssl rand -base64 64
```

Do not paste production secrets into:

```text
Git
documentation
tickets
chat
screenshots
```

---

## 13. Verify `.env.prod-jeevanam` Is Ignored by Git

```bash
git check-ignore -v local/.env.prod-jeevanam
```

Expected:

```text
.gitignore:...:.env.* local/.env.prod-jeevanam
```

The file must never be committed.

---

## 14. Configure Controlled Access

### Care

```dotenv
CLINIC_PATIENT_PORTAL_AUTH_MODE=ACCESS_APPROVAL
VITE_PATIENT_PORTAL_AUTH_MODE=ACCESS_APPROVAL
CLINIC_PATIENT_PORTAL_EXPOSE_DEV_OTP=false
```

### Provider

```dotenv
CLINIC_PROVIDER_PORTAL_AUTH_MODE=ACCESS_APPROVAL
VITE_PROVIDER_PORTAL_AUTH_MODE=ACCESS_APPROVAL
CLINIC_PROVIDER_PORTAL_EXPOSE_DEV_OTP=false
```

### Purpose

The existing OTP implementation remains available for future use, but Friends & Family access uses Platform Admin approval and temporary access credentials.

Development OTP must not be exposed in production.

---

## 15. Configure AI Providers

Production AI configuration:

```dotenv
CLINIC_AI_ENABLED=true
CLINIC_AI_PROVIDER=GEMINI

AI_PROVIDER=gemini
AI_FALLBACK_PROVIDER=groq
AI_PROVIDER_CHAIN=GEMINI,GROQ
AI_MOCK_ENABLED=false

CLINIC_GEMINI_ENABLED=true
GEMINI_API_KEY=<PRODUCTION_SECRET>
GEMINI_MODEL=gemini-2.5-flash

CLINIC_GROQ_ENABLED=true
GROQ_API_KEY=<PRODUCTION_SECRET>
GROQ_MODEL=llama-3.1-8b-instant

VOICE_LLM_PROVIDER_ORDER=gemini,groq
```

### Why remove `MOCK`

A production user should never silently receive a mock AI response when all real AI providers fail.

---

## 16. Configure Public URLs

```dotenv
PUBLIC_APP_URL=https://jeevanam.deepthoughtnet.com

VITE_DISCOVER_APP_URL=https://jeevanam.deepthoughtnet.com
VITE_CARE_APP_URL=https://care.jeevanam.deepthoughtnet.com
VITE_HEALTHCARE_APP_URL=https://health.jeevanam.deepthoughtnet.com
VITE_PROVIDER_APP_URL=https://jeevanam.deepthoughtnet.com/provider

VITE_AIVA_APP_URL=https://aiva.jeevanam.deepthoughtnet.com

API_BASE_URL=https://api.jeevanam.deepthoughtnet.com
VITE_API_BASE_URL=https://api.jeevanam.deepthoughtnet.com
VITE_PUBLIC_API_BASE_URL=https://api.jeevanam.deepthoughtnet.com

VITE_CLINIC_LOGIN_URL=https://health.jeevanam.deepthoughtnet.com
```

---

## 17. Configure Keycloak URLs

```dotenv
KEYCLOAK_URL=https://auth.jeevanam.deepthoughtnet.com/auth

VITE_KEYCLOAK_URL=https://auth.jeevanam.deepthoughtnet.com/auth
VITE_KEYCLOAK_REALM=clinic-management
VITE_KEYCLOAK_CLIENT_ID=clinic-web-admin
```

Backend JWT configuration:

```dotenv
CLINIC_JWT_ISSUER_URI=https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management

CLINIC_JWT_JWK_SET_URI=http://keycloak:8080/auth/realms/clinic-management/protocol/openid-connect/certs

CLINIC_KEYCLOAK_SERVER_URL=http://keycloak:8080/auth
```

### Design

Browser-facing URLs use public HTTPS.

Internal server-to-server calls use the Docker network.

---

## 18. Configure Database Connection

```dotenv
SPRING_PROFILES_ACTIVE=docker

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/clinic_management
SPRING_DATASOURCE_USERNAME=jeevanam_user
SPRING_DATASOURCE_PASSWORD=<SAME_POSTGRES_SECRET>
```

Prefer a single source of truth in Compose where practical:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
```

---

## 19. Configure CORS

```dotenv
CLINIC_CORS_ALLOWED_ORIGINS=https://jeevanam.deepthoughtnet.com,https://care.jeevanam.deepthoughtnet.com,https://health.jeevanam.deepthoughtnet.com,https://aiva.jeevanam.deepthoughtnet.com
```

Purpose:

Allow only approved Jeevanam browser applications to call the API.

---

## 20. Configure AIVA / Voice

```dotenv
VITE_AIVA_APP_URL=https://aiva.jeevanam.deepthoughtnet.com

VITE_CAREAI_RUNTIME_URL=wss://aiva.jeevanam.deepthoughtnet.com/ws/patient-portal/careai

VOICE_PATIENT_PORTAL_UPSTREAM_WS_URL=ws://clinic-management-api:8089/ws/patient-portal/careai

WHISPER_ENABLED=true
PIPER_ENABLED=true
AIVA_ENABLED=true

VOICE_TEST_ENABLED=false

VOICE_STT_PROVIDER_ORDER=faster-whisper,mock
VOICE_TTS_PROVIDER_ORDER=piper,mock
VOICE_LLM_PROVIDER_ORDER=gemini,groq

FASTER_WHISPER_BASE_URL=http://faster-whisper:8000
PIPER_TTS_BASE_URL=http://piper-tts:8001

PIPER_DEFAULT_VOICE=en_US-lessac-medium
PIPER_ENGLISH_VOICE=en_US-lessac-medium
PIPER_HINDI_VOICE=hi_IN-rohan-medium
PIPER_ALLOW_FALLBACK_VOICE=true
```

---

## 21. Validate the Production Environment File

Placeholder check:

```bash
grep -nE 'CHANGE_ME|<[^>]+>' .env.prod-jeevanam \
  || echo "No placeholders found"
```

AI key presence without printing keys:

```bash
grep -q '^GEMINI_API_KEY=.\+' .env.prod-jeevanam \
  && echo "Gemini key: configured" \
  || echo "Gemini key: MISSING"

grep -q '^GROQ_API_KEY=.\+' .env.prod-jeevanam \
  && echo "Groq key: configured" \
  || echo "Groq key: MISSING"
```

---

## 22. Validate Compose Before Starting Containers

```bash
cd /opt/jeevanam/clinic-management-platform/local

docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config
```

Fix all unset-variable warnings before continuing.

Final validation:

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config >/dev/null
```

This should return cleanly.

---

## 23. Validate the Service List

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config --services
```

Expected services:

```text
redis
faster-whisper
postgres
keycloak
minio
piper-tts
clinic-management-api
web-admin
web-aiva
web-care
whisper-stt
web-discover
realtime-voice-gateway
```

---

## 24. Verify Resolved Port Bindings

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config --format json \
| jq -r '
  .services
  | to_entries[]
  | select(.value.ports != null)
  | .key as $service
  | .value.ports[]
  | "\($service): host=\(.host_ip // "ALL") published=\(.published) -> container=\(.target)"
'
```

Expected:

```text
clinic-management-api: host=127.0.0.1 published=8089 -> container=8089
keycloak: host=127.0.0.1 published=8090 -> container=8080
realtime-voice-gateway: host=127.0.0.1 published=8091 -> container=8091
web-admin: host=127.0.0.1 published=5175 -> container=80
web-aiva: host=127.0.0.1 published=5177 -> container=80
web-care: host=127.0.0.1 published=5176 -> container=80
web-discover: host=127.0.0.1 published=5178 -> container=80
```

No public bindings should exist for:

```text
PostgreSQL
Redis
MinIO
Whisper
Faster-Whisper
Piper
```

---

## 25. Create DNS Records

Under the `deepthoughtnet.com` DNS zone create:

```text
Type: A
TTL: 300
```

Records:

```text
jeevanam        → 160.250.204.124
care.jeevanam   → 160.250.204.124
health.jeevanam → 160.250.204.124
api.jeevanam    → 160.250.204.124
auth.jeevanam   → 160.250.204.124
aiva.jeevanam   → 160.250.204.124
```

---

## 26. Verify DNS

```bash
for host in \
  jeevanam.deepthoughtnet.com \
  care.jeevanam.deepthoughtnet.com \
  health.jeevanam.deepthoughtnet.com \
  api.jeevanam.deepthoughtnet.com \
  auth.jeevanam.deepthoughtnet.com \
  aiva.jeevanam.deepthoughtnet.com
do
  printf "%-45s -> " "$host"
  dig +short "$host" | head -1
done
```

Every hostname should return:

```text
160.250.204.124
```

---

## 27. Fix `/etc/hosts` Overrides If Present

If:

```bash
getent hosts jeevanam.deepthoughtnet.com
```

returns `127.0.0.1` while:

```bash
dig +short jeevanam.deepthoughtnet.com
```

returns the public IP, inspect:

```bash
cat /etc/hosts
```

If this line exists:

```text
127.0.0.1 jeevanam.deepthoughtnet.com
```

back up the file:

```bash
cp /etc/hosts /etc/hosts.backup-2026-08-14
```

Then remove only that override:

```bash
sed -i \
'/127\.0\.0\.1[[:space:]]\+jeevanam\.deepthoughtnet\.com/d' \
/etc/hosts
```

Verify:

```bash
getent hosts jeevanam.deepthoughtnet.com
dig +short jeevanam.deepthoughtnet.com
```

Both should resolve to:

```text
160.250.204.124
```

---

## 28. Install Nginx and Certbot

```bash
apt update

apt install -y \
  nginx \
  certbot \
  python3-certbot-nginx
```

Verify:

```bash
nginx -v
certbot --version
systemctl is-active nginx
```

Check listeners:

```bash
ss -tulpn | grep -E ':80|:443'
```

---

## 29. Create Nginx Reverse Proxy

Create:

```text
/etc/nginx/sites-available/jeevanam
```

Contents:

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name jeevanam.deepthoughtnet.com;

    location / {
        proxy_pass http://127.0.0.1:5178;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    listen [::]:80;
    server_name care.jeevanam.deepthoughtnet.com;

    location / {
        proxy_pass http://127.0.0.1:5176;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    listen [::]:80;
    server_name health.jeevanam.deepthoughtnet.com;

    location / {
        proxy_pass http://127.0.0.1:5175;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    listen [::]:80;
    server_name api.jeevanam.deepthoughtnet.com;

    location / {
        proxy_pass http://127.0.0.1:8089;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    listen [::]:80;
    server_name auth.jeevanam.deepthoughtnet.com;

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    listen [::]:80;
    server_name aiva.jeevanam.deepthoughtnet.com;

    location / {
        proxy_pass http://127.0.0.1:5177;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8091;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Validate:

```bash
nginx -t
```

Expected:

```text
syntax is ok
test is successful
```

---

## 30. Enable the Jeevanam Nginx Site

```bash
ln -s \
  /etc/nginx/sites-available/jeevanam \
  /etc/nginx/sites-enabled/jeevanam
```

Remove the default site:

```bash
rm -f /etc/nginx/sites-enabled/default
```

Validate:

```bash
nginx -t
```

Reload:

```bash
systemctl reload nginx
```

Verify:

```bash
ls -l /etc/nginx/sites-enabled
systemctl is-active nginx
```

---

## 31. Issue TLS Certificates

Request one certificate containing all six domains:

```bash
certbot --nginx \
  -d jeevanam.deepthoughtnet.com \
  -d care.jeevanam.deepthoughtnet.com \
  -d health.jeevanam.deepthoughtnet.com \
  -d api.jeevanam.deepthoughtnet.com \
  -d auth.jeevanam.deepthoughtnet.com \
  -d aiva.jeevanam.deepthoughtnet.com
```

Choose HTTP-to-HTTPS redirection when prompted.

Certificate files:

```text
/etc/letsencrypt/live/jeevanam.deepthoughtnet.com/fullchain.pem
/etc/letsencrypt/live/jeevanam.deepthoughtnet.com/privkey.pem
```

Verify:

```bash
nginx -t
systemctl reload nginx

ss -tulpn | grep ':443'

certbot certificates
```

Certbot automatically installs renewal scheduling.

---

## 32. Expected `502 Bad Gateway` Before Container Startup

Before Jeevanam containers are started:

```bash
curl -I https://jeevanam.deepthoughtnet.com
```

may return:

```text
502 Bad Gateway
```

This is expected if Nginx is working but no application is listening on the upstream localhost port.

---

## 33. Start the Production Stack

```bash
cd /opt/jeevanam/clinic-management-platform/local

docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  up -d --build
```

Check:

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  ps
```

Also:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

---

## 34. Initial Keycloak Startup Failure

The first production startup failed because Keycloak could not find its database.

Logs:

```bash
docker logs --tail=250 jeevanam-prod-keycloak
```

Error:

```text
FATAL: database "keycloak" does not exist
```

Inspect the databases:

```bash
docker exec -it jeevanam-prod-postgres \
  psql -U jeevanam_user -d clinic_management \
  -c '\l'
```

At the time only:

```text
clinic_management
postgres
template0
template1
```

existed.

---

## 35. Root Cause of the Missing Keycloak Database

Initializer:

```text
local/init-sql/001_create_keycloak_db.sql
```

contained:

```sql
SELECT 'CREATE DATABASE keycloak OWNER clinic'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'keycloak'
) \gexec
```

Production uses:

```text
POSTGRES_USER=jeevanam_user
```

The initializer was therefore environment-specific.

Also note that files in `/docker-entrypoint-initdb.d` run only when PostgreSQL initializes a fresh data directory.

---

## 36. Create the Keycloak Database Manually

For the already-initialized production database:

```bash
docker exec -it jeevanam-prod-postgres \
  psql -U jeevanam_user -d postgres \
  -c 'CREATE DATABASE keycloak OWNER jeevanam_user;'
```

Verify:

```bash
docker exec -it jeevanam-prod-postgres \
  psql -U jeevanam_user -d postgres \
  -c '\l'
```

Expected:

```text
clinic_management | jeevanam_user
keycloak          | jeevanam_user
```

---

## 37. Future Fix for `001_create_keycloak_db.sql`

The production-safe version should not hardcode owner `clinic`.

Recommended:

```sql
-- Create the Keycloak database automatically when the Postgres data
-- directory is initialized for the first time.

SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'keycloak'
) \gexec
```

The executing `POSTGRES_USER` will become the database owner.

---

## 38. Restart Keycloak

```bash
docker restart jeevanam-prod-keycloak
```

Then:

```bash
docker ps --filter name=jeevanam-prod-keycloak \
  --format "table {{.Names}}\t{{.Status}}"
```

Inspect logs:

```bash
docker logs --tail=120 jeevanam-prod-keycloak
```

On first successful start Keycloak initializes:

```text
schema
master realm
clinic-management realm
bootstrap admin
```

---

## 39. Bring Up Dependent Services

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  up -d
```

Expected final state:

```text
PostgreSQL                healthy
Keycloak                  healthy
API                       healthy
web-admin                 healthy
web-care                  healthy
web-discover              healthy
web-aiva                  healthy
Realtime Voice Gateway    healthy
Whisper                   healthy
Faster-Whisper            healthy
Piper                     healthy
Redis                     running
MinIO                     running
```

---

## 40. Run Keycloak in Production Mode

Do not use:

```yaml
command: start-dev --import-realm
```

Production must use:

```yaml
command: start --import-realm
```

Recommended Keycloak service fragment:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:24.0
  container_name: ${CONTAINER_NAME_PREFIX:-jeevanam-prod}-keycloak
  restart: unless-stopped
  command: start --import-realm

  environment:
    KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN:-admin}
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}

    KC_HEALTH_ENABLED: "true"

    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    KC_DB_USERNAME: ${POSTGRES_USER:-clinic}
    KC_DB_PASSWORD: ${POSTGRES_PASSWORD}

    KC_HOSTNAME_URL: ${KEYCLOAK_URL}
    KC_HTTP_RELATIVE_PATH: /auth

    KC_HOSTNAME_STRICT: "false"
    KC_HOSTNAME_STRICT_HTTPS: "true"

    KC_PROXY_HEADERS: xforwarded
    KC_HTTP_ENABLED: "true"
```

### Why `KC_HTTP_ENABLED=true`

Nginx performs TLS termination.

Traffic path:

```text
Browser
  ↓ HTTPS
Nginx
  ↓ HTTP localhost
Keycloak
```

---

## 41. Recreate Only Keycloak

After production-mode changes:

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  up -d --force-recreate keycloak
```

Inspect:

```bash
docker logs --tail=100 jeevanam-prod-keycloak
```

Expected:

```text
Profile prod activated.
```

The development-mode warning must not appear.

---

## 42. Internal Smoke Test

API:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  http://127.0.0.1:8089/actuator/health
```

Keycloak:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  http://127.0.0.1:8090/auth/realms/clinic-management
```

Discover:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  http://127.0.0.1:5178/
```

Care:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  http://127.0.0.1:5176/
```

Healthcare:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  http://127.0.0.1:5175/
```

AIVA:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  http://127.0.0.1:5177/
```

Expected:

```text
200
```

for all six.

---

## 43. Public HTTPS Smoke Test

```bash
for host in \
  jeevanam.deepthoughtnet.com \
  care.jeevanam.deepthoughtnet.com \
  health.jeevanam.deepthoughtnet.com \
  api.jeevanam.deepthoughtnet.com \
  auth.jeevanam.deepthoughtnet.com \
  aiva.jeevanam.deepthoughtnet.com
do
  echo "===== $host ====="
  curl -sS -o /dev/null -w "%{http_code}\n" "https://$host"
done
```

Typical results:

```text
Discover    200
Care        200
Healthcare  200
API         401
Auth        404
AIVA        200
```

`401` on the API root is acceptable if `/` is protected.

`404` on the Keycloak root is acceptable because Keycloak is mounted at `/auth`.

---

## 44. Validate Meaningful API and Keycloak Endpoints

API health:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://api.jeevanam.deepthoughtnet.com/actuator/health
```

Keycloak realm:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management
```

OIDC discovery:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management/.well-known/openid-configuration
```

Expected:

```text
200
200
200
```

---

## 45. Keycloak `redirect_uri` Production Defect

Initial Healthcare sign-in failed with:

```text
Invalid parameter: redirect_uri
```

The browser requested:

```text
client_id=clinic-web-admin

redirect_uri=https://health.jeevanam.deepthoughtnet.com/
```

Inspect the Keycloak client:

```bash
docker exec jeevanam-prod-keycloak \
  /opt/keycloak/bin/kcadm.sh get clients \
  -r clinic-management \
  -q clientId=clinic-web-admin \
  --fields id,clientId,redirectUris,webOrigins
```

Initially only localhost URLs existed.

---

## 46. Update the Live Keycloak Client

The production client ID was:

```text
81dc2826-32d0-481b-8600-a9506d9e92c7
```

Add:

```text
https://health.jeevanam.deepthoughtnet.com/*
```

to redirect URIs and:

```text
https://health.jeevanam.deepthoughtnet.com
```

to web origins.

Preserve local URLs.

Example:

```bash
docker exec jeevanam-prod-keycloak \
  /opt/keycloak/bin/kcadm.sh update clients/81dc2826-32d0-481b-8600-a9506d9e92c7 \
  -r clinic-management \
  -s 'redirectUris=[
    "http://127.0.0.1:3000/*",
    "http://localhost:3000/*",
    "http://localhost:5173/*",
    "http://127.0.0.1:5173/*",
    "https://health.jeevanam.deepthoughtnet.com/*"
  ]' \
  -s 'webOrigins=[
    "http://127.0.0.1:3000",
    "http://localhost:3000",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "https://health.jeevanam.deepthoughtnet.com"
  ]'
```

---

## 47. Persist Keycloak Redirect Configuration in Source

Update:

```text
local/keycloak/realm-export.json
```

The `clinic-web-admin` client should include:

```json
"redirectUris": [
  "http://localhost:5173/*",
  "http://127.0.0.1:5173/*",
  "http://localhost:3000/*",
  "http://127.0.0.1:3000/*",
  "https://health.jeevanam.deepthoughtnet.com/*"
],
"webOrigins": [
  "http://localhost:5173",
  "http://127.0.0.1:5173",
  "http://localhost:3000",
  "http://127.0.0.1:3000",
  "https://health.jeevanam.deepthoughtnet.com"
]
```

Validate:

```bash
jq empty local/keycloak/realm-export.json
```

Committed as:

```text
c3481e0
fix: allow Jeevanam Healthcare production redirect in Keycloak
```

---

## 48. Production Authentication Validation

Successful flow:

```text
Healthcare
→ Keycloak
→ Platform Admin authentication
→ Healthcare
→ /platform/tenants
```

This proves:

```text
HTTPS
→ Healthcare frontend
→ Keycloak
→ token issuance
→ API authorization
→ Platform data
```

---

## 49. Tenant Context Validation

From:

```text
/platform/tenants
```

open:

```text
Demo Clinic
```

The system successfully entered tenant context and loaded:

```text
/dashboard
```

with tenant-specific modules.

This validates:

```text
Platform context
→ tenant selection
→ tenant context
→ tenant-aware API access
```

---

# Backup and Recovery

## 50. Production Backup Directory Structure

Create:

```bash
mkdir -p \
  /opt/jeevanam/backups/postgres/clinic_management \
  /opt/jeevanam/backups/postgres/keycloak \
  /opt/jeevanam/backups/minio
```

Protect:

```bash
chmod 700 /opt/jeevanam/backups
```

Result:

```text
/opt/jeevanam/backups/
├── postgres/
│   ├── clinic_management/
│   └── keycloak/
└── minio/
```

---

## 51. Production Backup Script

Runtime location:

```text
/opt/jeevanam/bin/backup-jeevanam-prod.sh
```

Canonical Git copy:

```text
local/scripts/backup-production.sh
```

The script backs up:

```text
clinic_management PostgreSQL DB
keycloak PostgreSQL DB
MinIO objects
```

It also performs:

```text
non-empty validation
pg_restore listing validation
SHA-256 checksums
14-day local retention
timestamped logging
```

---

## 52. Fix CRLF on Shell Scripts if Required

If execution reports interpreter issues, inspect:

```bash
file /opt/jeevanam/bin/backup-jeevanam-prod.sh
```

If it reports:

```text
with CRLF line terminators
```

convert:

```bash
sed -i 's/\r$//' \
  /opt/jeevanam/bin/backup-jeevanam-prod.sh
```

Run:

```bash
bash /opt/jeevanam/bin/backup-jeevanam-prod.sh
```

---

## 53. First Successful Backup

A successful production backup produced approximately:

```text
clinic_management  676K
keycloak           212K
MinIO              4.0K
```

A small MinIO backup is expected on a new system with little or no object data.

---

## 54. Systemd Backup Service

File:

```text
/etc/systemd/system/jeevanam-backup.service
```

Contents:

```ini
[Unit]
Description=Jeevanam Production Backup
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
ExecStart=/bin/bash /opt/jeevanam/bin/backup-jeevanam-prod.sh
User=root
```

---

## 55. Systemd Backup Timer

File:

```text
/etc/systemd/system/jeevanam-backup.timer
```

Contents:

```ini
[Unit]
Description=Daily Jeevanam Production Backup

[Timer]
OnCalendar=*-*-* 02:00:00
Persistent=true
Unit=jeevanam-backup.service

[Install]
WantedBy=timers.target
```

Enable:

```bash
systemctl daemon-reload
systemctl enable --now jeevanam-backup.timer
```

Verify:

```bash
systemctl status jeevanam-backup.timer --no-pager

systemctl list-timers jeevanam-backup.timer --all
```

Expected next run:

```text
02:00 IST
```

---

## 56. Validate the Systemd Backup Path

Run:

```bash
systemctl start jeevanam-backup.service
```

Then:

```bash
systemctl status jeevanam-backup.service --no-pager

journalctl \
  -u jeevanam-backup.service \
  -n 50 \
  --no-pager
```

A successful oneshot service ends as:

```text
Deactivated successfully
Finished Jeevanam Production Backup
```

---

## 57. Version-Control Production Infrastructure

The tested production files are committed under:

```text
local/docker-compose.prod.yml
local/scripts/backup-production.sh
local/systemd/jeevanam-backup.service
local/systemd/jeevanam-backup.timer
```

The infrastructure baseline commit is:

```text
b403576
ops: add Jeevanam production deployment and backup baseline
```

---

## 58. Normalize Line Endings Before Committing

If staged diffs show `^M`, convert:

```bash
sed -i 's/\r$//' \
  local/docker-compose.prod.yml \
  local/scripts/backup-production.sh \
  local/systemd/jeevanam-backup.service \
  local/systemd/jeevanam-backup.timer
```

Verify:

```bash
file \
  local/docker-compose.prod.yml \
  local/scripts/backup-production.sh \
  local/systemd/jeevanam-backup.service \
  local/systemd/jeevanam-backup.timer
```

No file should report CRLF line endings.

Recommended future `.gitattributes`:

```gitattributes
*.sh text eol=lf
*.yml text eol=lf
*.yaml text eol=lf
*.service text eol=lf
*.timer text eol=lf
*.json text eol=lf
```

---

# Operations

## 59. Normal Production Start / Deploy

From:

```bash
cd /opt/jeevanam/clinic-management-platform/local
```

Build and start:

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  up -d --build
```

Normal restart without rebuilding:

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  up -d
```

---

## 60. Container Health

```bash
docker ps \
  --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Compose-level status:

```bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  ps
```

---

## 61. API Health

```bash
curl -sS \
  https://api.jeevanam.deepthoughtnet.com/actuator/health
```

Expected HTTP status:

```text
200
```

---

## 62. Keycloak Health / OIDC

```bash
curl -sS \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management/.well-known/openid-configuration
```

Expected HTTP status:

```text
200
```

---

## 63. Nginx

Validate:

```bash
nginx -t
```

Status:

```bash
systemctl status nginx
```

Reload after configuration change:

```bash
systemctl reload nginx
```

---

## 64. Certificates

```bash
certbot certificates
```

Check renewal timer:

```bash
systemctl list-timers | grep certbot
```

---

## 65. Backup Timer

```bash
systemctl status jeevanam-backup.timer --no-pager

systemctl list-timers jeevanam-backup.timer --all
```

Latest logs:

```bash
journalctl \
  -u jeevanam-backup.service \
  -n 100 \
  --no-pager
```

---

## 66. Manual Backup Before Risky Changes

Before:

```text
database migration
major release
Keycloak change
MinIO migration
infrastructure upgrade
```

run:

```bash
systemctl start jeevanam-backup.service
```

Then verify:

```bash
journalctl \
  -u jeevanam-backup.service \
  -n 50 \
  --no-pager
```

---

# Safety

## 67. Commands to Avoid

Do not casually run:

```bash
docker compose down -v
```

The `-v` option can delete persistent Docker volumes.

Do not remove:

```text
jeevanam_prod_postgres_data
```

Do not remove MinIO volumes.

Do not delete:

```text
/opt/jeevanam/backups
```

Do not commit:

```text
local/.env.prod-jeevanam
```

Do not publicly expose:

```text
PostgreSQL 5432
Redis 6379
MinIO 9000
Whisper
Faster-Whisper
Piper
```

---

## 68. Production Secret Handling

Real secrets live only in:

```text
local/.env.prod-jeevanam
```

Recommended file permissions:

```bash
chmod 600 local/.env.prod-jeevanam
chown root:root local/.env.prod-jeevanam
```

Never include production secrets in:

```text
Git
documentation
screenshots
terminal transcripts shared externally
support tickets
chat
```

---

# Remaining Hardening Work

## 69. Off-VPS Backup

Current backup copies are stored on the production VPS.

This protects against:

```text
application error
accidental DB change
some corruption scenarios
deployment mistakes
```

but not against total VPS/disk loss.

Add an encrypted off-server copy to:

```text
another VPS
S3-compatible storage
cloud object storage
secure NAS
```

---

## 70. Restore Drill

Backups are not fully trusted until restore is tested.

Required drill:

```text
restore clinic_management into isolated DB
restore keycloak into isolated DB
restore MinIO objects into isolated location
validate checksums
run application smoke tests
```

Do not test restoration by overwriting the live production database.

---

## 71. PostgreSQL PITR / WAL Archiving

The current backup system uses logical dumps.

Future hardening should add:

```text
WAL archiving
point-in-time recovery
periodic full-cluster backup
```

This reduces the recovery-point objective compared with nightly backups.

---

## 72. SMTP

SMTP is intentionally disabled.

When ready, configure:

```dotenv
CLINIC_MAIL_ENABLED=true
CLINIC_MAIL_HOST=<SMTP_HOST>
CLINIC_MAIL_USERNAME=<SMTP_USERNAME>
CLINIC_MAIL_PASSWORD=<SECRET>
CLINIC_MAIL_FROM_EMAIL=<FROM_ADDRESS>
```

Then validate notifications separately.

---

## 73. Monitoring / Alerting

Recommended alerts:

```text
API unhealthy
Keycloak unhealthy
PostgreSQL unhealthy
container restart loops
disk > threshold
RAM > threshold
backup failure
certificate renewal failure
MinIO unavailable
voice gateway unavailable
```

---

# Current Production Validation Status

## 74. Verified Components

```text
DNS                              PASS
HTTPS / Let's Encrypt            PASS
Nginx                             PASS
Discover                          PASS
Care                              PASS
Healthcare                        PASS
AIVA frontend                     PASS
API                               PASS
PostgreSQL                        PASS
Redis                             PASS
MinIO                             PASS
Keycloak                          PASS
Keycloak PROD mode                PASS
OIDC discovery                    PASS
Healthcare authentication         PASS
Platform Admin                    PASS
Tenant selection                  PASS
Demo Clinic tenant context        PASS
Voice Gateway                     PASS
Whisper                           PASS
Faster-Whisper                    PASS
Piper                             PASS
Care ACCESS_APPROVAL              CONFIGURED
Provider ACCESS_APPROVAL          CONFIGURED
Development OTP exposure          DISABLED
Gemini                            CONFIGURED
Groq fallback                     CONFIGURED
Mock AI                           DISABLED
DB local backup                   PASS
Keycloak DB backup                PASS
MinIO backup                      PASS
Daily systemd backup              PASS
14-day retention                  PASS
Production infrastructure in Git  PASS
Keycloak redirect in Git          PASS
```

---

# Git Baselines

## 75. Production Deployment / Backup Commit

```text
b403576
ops: add Jeevanam production deployment and backup baseline
```

## 76. Keycloak Production Redirect Commit

```text
c3481e0
fix: allow Jeevanam Healthcare production redirect in Keycloak
```

---

# UAT Continuation Point

## 77. Where Production UAT Paused

Validated flow:

```text
Platform Admin
→ Platform Tenants
→ Demo Clinic
→ Tenant Dashboard
```

The next planned functional test is:

```text
Appointment
→ Registration
→ Check-in
→ Queue
→ Consultation
→ Prescription
→ Billing
→ Completion
```

This should be tested one step at a time before wider Friends & Family / clinic access is enabled.

---

# Quick Reference

## 78. Production URLs

```text
Discover:
https://jeevanam.deepthoughtnet.com

Care:
https://care.jeevanam.deepthoughtnet.com

Healthcare:
https://health.jeevanam.deepthoughtnet.com

API:
https://api.jeevanam.deepthoughtnet.com

Keycloak:
https://auth.jeevanam.deepthoughtnet.com/auth

AIVA:
https://aiva.jeevanam.deepthoughtnet.com
```

---

## 79. Important Paths

```text
Repository:
/opt/jeevanam/clinic-management-platform

Production Compose:
/opt/jeevanam/clinic-management-platform/local/docker-compose.prod.yml

Production Environment:
/opt/jeevanam/clinic-management-platform/local/.env.prod-jeevanam

Nginx:
/etc/nginx/sites-available/jeevanam

Backups:
/opt/jeevanam/backups

Backup Runtime Script:
/opt/jeevanam/bin/backup-jeevanam-prod.sh

Backup Service:
/etc/systemd/system/jeevanam-backup.service

Backup Timer:
/etc/systemd/system/jeevanam-backup.timer
```

---

## 80. Fast Health Check

```bash
echo "===== CONTAINERS ====="
docker ps --format "table {{.Names}}\t{{.Status}}"

echo
echo "===== API ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://api.jeevanam.deepthoughtnet.com/actuator/health

echo
echo "===== KEYCLOAK ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management/.well-known/openid-configuration

echo
echo "===== NGINX ====="
nginx -t

echo
echo "===== BACKUP TIMER ====="
systemctl list-timers jeevanam-backup.timer --all
```

---

**End of Jeevanam Production Deployment Runbook**
