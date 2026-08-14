# Jeevanam Production Release Process

**Document:** `JEEVANAM_PRODUCTION_RELEASE_PROCESS.md`\
**Purpose:** Standard operating procedure for deploying new Jeevanam
application changes to the production VPS safely and consistently.

> **Important:** Production releases should be deployed from a known Git
> tag whenever possible. Always take a production backup before changing
> the deployed version.

------------------------------------------------------------------------

## Table of Contents

1.  [Release Flow](#1-release-flow)
2.  [Before You Start](#2-before-you-start)
3.  [Step 1 --- Test Changes on Development
    Machine](#3-step-1--test-changes-on-development-machine)
4.  [Step 2 --- Commit the Changes](#4-step-2--commit-the-changes)
5.  [Step 3 --- Push to GitHub](#5-step-3--push-to-github)
6.  [Step 4 --- Create a Production Release
    Tag](#6-step-4--create-a-production-release-tag)
7.  [Step 5 --- Take a Production
    Backup](#7-step-5--take-a-production-backup)
8.  [Step 6 --- Check Current Production
    State](#8-step-6--check-current-production-state)
9.  [Step 7 --- Fetch the New Release on the
    VPS](#9-step-7--fetch-the-new-release-on-the-vps)
10. [Step 8 --- Checkout the Release
    Tag](#10-step-8--checkout-the-release-tag)
11. [Step 9 --- Validate Production Compose
    Configuration](#11-step-9--validate-production-compose-configuration)
12. [Step 10 --- Deploy the New
    Version](#12-step-10--deploy-the-new-version)
13. [Step 11 --- Verify Container
    Health](#13-step-11--verify-container-health)
14. [Step 12 --- Run Production Smoke
    Tests](#14-step-12--run-production-smoke-tests)
15. [Step 13 --- Functional Smoke
    Test](#15-step-13--functional-smoke-test)
16. [Step 14 --- Record the Release](#16-step-14--record-the-release)
17. [Rollback Procedure](#17-rollback-procedure)
18. [Database Migration Precautions](#18-database-migration-precautions)
19. [Special Cases](#19-special-cases)
20. [Commands You Must Avoid](#20-commands-you-must-avoid)
21. [Quick Release Checklist](#21-quick-release-checklist)
22. [Quick Command Reference](#22-quick-command-reference)

------------------------------------------------------------------------

# 1. Release Flow

The standard Jeevanam production release flow is:

``` text
Development
    ↓
Implement Changes
    ↓
Local/UAT Testing
    ↓
Commit
    ↓
Push to GitHub
    ↓
Create Production Tag
    ↓
Production Backup
    ↓
VPS Fetch
    ↓
Checkout Release Tag
    ↓
Validate Production Configuration
    ↓
Build/Recreate Containers
    ↓
Container Health Check
    ↓
HTTPS Smoke Tests
    ↓
Functional Smoke Test
    ↓
Release Complete
```

### Why use this process?

It gives us:

-   a known production version;
-   a backup before every deployment;
-   reproducible releases;
-   an easy rollback point;
-   separation between development and production;
-   verification before users access the new release.

------------------------------------------------------------------------

# 2. Before You Start

Production repository:

``` text
/opt/jeevanam/clinic-management-platform
```

Production Compose:

``` text
/opt/jeevanam/clinic-management-platform/local/docker-compose.prod.yml
```

Production environment:

``` text
/opt/jeevanam/clinic-management-platform/local/.env.prod-jeevanam
```

Production backup service:

``` text
jeevanam-backup.service
```

### Production applications

``` text
Discover:
https://jeevanam.deepthoughtnet.com

Care:
https://care.jeevanam.deepthoughtnet.com

Healthcare:
https://health.jeevanam.deepthoughtnet.com

API:
https://api.jeevanam.deepthoughtnet.com

Authentication:
https://auth.jeevanam.deepthoughtnet.com/auth

AIVA:
https://aiva.jeevanam.deepthoughtnet.com
```

------------------------------------------------------------------------

# 3. Step 1 --- Test Changes on Development Machine

Before committing, verify that the change works locally/UAT.

From the development machine:

``` bash
cd ~/code/clinic-management-platform

git status
```

Review the changed files carefully.

``` bash
git diff
```

If some files are already staged:

``` bash
git diff --cached
```

### Purpose

Do not use production as the first test environment.

Check especially:

-   application starts successfully;
-   frontend builds;
-   backend tests pass;
-   database migrations are valid;
-   authentication still works;
-   no development-only configuration is accidentally introduced;
-   no secrets are present in source files.

------------------------------------------------------------------------

# 4. Step 2 --- Commit the Changes

Check:

``` bash
git status
```

Stage the intended files.

For all intended changes:

``` bash
git add .
```

Or preferably stage specific files when appropriate:

``` bash
git add path/to/file1 path/to/file2
```

Review what will be committed:

``` bash
git diff --cached
```

Commit:

``` bash
git commit -m "feat: describe the production change"
```

Example:

``` bash
git commit -m "feat: improve appointment workflow"
```

Verify:

``` bash
git log -1 --oneline
```

------------------------------------------------------------------------

# 5. Step 3 --- Push to GitHub

Push the commit:

``` bash
git push origin main
```

Verify the local state:

``` bash
git status
```

Expected:

``` text
nothing to commit, working tree clean
```

### Purpose

GitHub becomes the source from which the VPS retrieves the release. Do
not manually copy application source files to production.

------------------------------------------------------------------------

# 6. Step 4 --- Create a Production Release Tag

For production, prefer deploying an immutable tag rather than whatever
happens to be at the tip of `main`.

Recommended naming pattern:

``` text
jeevanam-prod-YYYY-MM-DD-NN
```

Example:

``` text
jeevanam-prod-2026-08-15-01
```

Create the tag:

``` bash
git tag jeevanam-prod-2026-08-15-01
```

Push it:

``` bash
git push origin jeevanam-prod-2026-08-15-01
```

Verify:

``` bash
git show --stat jeevanam-prod-2026-08-15-01
```

### Why tag production releases?

Suppose Release 2 causes a problem.

Instead of guessing which commit was previously running, we can simply
return to:

``` text
jeevanam-prod-2026-08-14-01
```

This makes rollback predictable.

> Do not move or reuse a production tag after it has been deployed.
> Create a new tag for a corrected release.

------------------------------------------------------------------------

# 7. Step 5 --- Take a Production Backup

SSH into the production VPS.

Before pulling or checking out new code:

``` bash
systemctl start jeevanam-backup.service
```

Wait for completion and inspect the log:

``` bash
journalctl \
  -u jeevanam-backup.service \
  -n 50 \
  --no-pager
```

Look for:

``` text
Backup completed successfully.
```

The backup should include:

``` text
clinic_management database
Keycloak database
MinIO objects
checksums
```

### Purpose

If a deployment includes a bad migration or causes unexpected data
problems, we have a pre-release recovery point.

**Do not continue with the production deployment if the pre-release
backup fails.**

------------------------------------------------------------------------

# 8. Step 6 --- Check Current Production State

On the VPS:

``` bash
cd /opt/jeevanam/clinic-management-platform
```

Check:

``` bash
git status
```

Then:

``` bash
git log -1 --oneline
```

And:

``` bash
git describe --tags --always
```

Also record the exact current commit:

``` bash
git rev-parse HEAD
```

### Required condition

Production source should be clean:

``` text
nothing to commit, working tree clean
```

If `git status` shows modified files, **do not immediately pull or
checkout the new release**.

First determine why production contains local modifications.

Production application source should normally not be edited directly.

------------------------------------------------------------------------

# 9. Step 7 --- Fetch the New Release on the VPS

Fetch the repository:

``` bash
git fetch origin
```

Fetch tags:

``` bash
git fetch --tags
```

Confirm that the new tag exists:

``` bash
git tag --list "jeevanam-prod-2026-08-15-01"
```

Inspect it before deployment:

``` bash
git show --stat jeevanam-prod-2026-08-15-01
```

Optional comparison with the currently deployed version:

``` bash
git diff --stat HEAD..jeevanam-prod-2026-08-15-01
```

For detailed changes:

``` bash
git log --oneline HEAD..jeevanam-prod-2026-08-15-01
```

------------------------------------------------------------------------

# 10. Step 8 --- Checkout the Release Tag

Deploy the exact release:

``` bash
git checkout jeevanam-prod-2026-08-15-01
```

Verify:

``` bash
git status
git log -1 --oneline
git describe --tags --always
```

The displayed tag should be the release you intend to deploy.

### Note about detached HEAD

Checking out a tag normally puts Git into detached HEAD state.

That is acceptable for a production deployment because production is
consuming a fixed release, not developing new code.

------------------------------------------------------------------------

# 11. Step 9 --- Validate Production Compose Configuration

Move to:

``` bash
cd /opt/jeevanam/clinic-management-platform/local
```

Validate the resolved Compose configuration:

``` bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config >/dev/null
```

### Expected result

No Compose configuration error should be returned.

If warnings appear about missing required production environment
variables, stop and investigate them before deployment.

### Important

Do not print or commit the contents of:

``` text
.env.prod-jeevanam
```

It contains production secrets.

------------------------------------------------------------------------

# 12. Step 10 --- Deploy the New Version

For the normal full Jeevanam release:

``` bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  up -d --build
```

### What this does

Docker Compose will:

1.  build images whose build context has changed;
2.  reuse unchanged layers where possible;
3.  recreate containers whose configuration/image changed;
4.  keep persistent named volumes;
5.  start the required dependency chain.

It does **not** require deleting the PostgreSQL volume.

### Important

Do not add:

``` text
-v
```

to a `docker compose down` operation.

------------------------------------------------------------------------

# 13. Step 11 --- Verify Container Health

Immediately after deployment:

``` bash
docker ps \
  --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

You should see the production services.

Important containers include:

``` text
jeevanam-prod-postgres
jeevanam-prod-redis
jeevanam-prod-minio
jeevanam-prod-keycloak
jeevanam-prod-api
jeevanam-prod-web-admin
jeevanam-prod-web-care
jeevanam-prod-web-discover
jeevanam-prod-web-aiva
jeevanam-prod-realtime-voice-gateway
jeevanam-prod-whisper-stt
jeevanam-prod-faster-whisper
jeevanam-prod-piper-tts
```

For Compose status:

``` bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  ps
```

### If a container is unhealthy

Do not repeatedly restart everything.

Inspect the failing service first.

Example:

``` bash
docker logs --tail=200 jeevanam-prod-api
```

Keycloak:

``` bash
docker logs --tail=200 jeevanam-prod-keycloak
```

Frontend:

``` bash
docker logs --tail=200 jeevanam-prod-web-admin
```

------------------------------------------------------------------------

# 14. Step 12 --- Run Production Smoke Tests

## API

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://api.jeevanam.deepthoughtnet.com/actuator/health
```

Expected:

``` text
200
```

## Keycloak OIDC

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management/.well-known/openid-configuration
```

Expected:

``` text
200
```

## Discover

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://jeevanam.deepthoughtnet.com
```

Expected:

``` text
200
```

## Care

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://care.jeevanam.deepthoughtnet.com
```

Expected:

``` text
200
```

## Healthcare

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://health.jeevanam.deepthoughtnet.com
```

Expected:

``` text
200
```

## AIVA

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://aiva.jeevanam.deepthoughtnet.com
```

Expected:

``` text
200
```

### Combined check

``` bash
echo "===== API ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://api.jeevanam.deepthoughtnet.com/actuator/health

echo "===== KEYCLOAK ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management/.well-known/openid-configuration

echo "===== DISCOVER ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://jeevanam.deepthoughtnet.com

echo "===== CARE ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://care.jeevanam.deepthoughtnet.com

echo "===== HEALTHCARE ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://health.jeevanam.deepthoughtnet.com

echo "===== AIVA ====="
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://aiva.jeevanam.deepthoughtnet.com
```

Expected:

``` text
API         200
KEYCLOAK    200
DISCOVER    200
CARE        200
HEALTHCARE  200
AIVA        200
```

------------------------------------------------------------------------

# 15. Step 13 --- Functional Smoke Test

HTTP `200` is necessary but does not prove that the business application
works.

After every meaningful production release, perform a small functional
test.

Minimum recommended test:

``` text
1. Open Healthcare
2. Sign in through Keycloak
3. Confirm Platform Admin/Tenant login works
4. Open Demo Clinic or intended tenant
5. Confirm dashboard loads
6. Open the module affected by the release
7. Perform one safe read/write operation if appropriate
8. Confirm no unexpected browser/API errors
```

For changes affecting the core clinical lifecycle, test the relevant
portion of:

``` text
Registration
→ Appointment
→ Check-in
→ Queue
→ Consultation
→ Prescription
→ Billing
→ Completion
```

For Care changes:

``` text
Care login/access
→ affected workspace
→ affected action
```

For Discover changes:

``` text
Discover
→ search/provider/clinic flow
→ affected action
```

For AIVA changes:

``` text
AIVA UI
→ WebSocket/runtime connection
→ STT
→ LLM
→ TTS
```

Only test the paths relevant to the release plus the critical
login/health baseline.

------------------------------------------------------------------------

# 16. Step 14 --- Record the Release

After validation, record:

``` text
Release tag
Git commit
Deployment date/time
Backup timestamp
Deployment result
Smoke-test result
Any migration executed
Any known issue
Rollback tag
```

Useful commands:

``` bash
git describe --tags --always
git rev-parse HEAD
git log -1 --oneline
```

Example release record:

``` text
Release: jeevanam-prod-2026-08-15-01
Commit: abc1234
Backup: 20260815_001500
Deployment: PASS
API Health: PASS
Keycloak: PASS
Discover: PASS
Care: PASS
Healthcare: PASS
AIVA: PASS
Functional Smoke Test: PASS
Previous Release: jeevanam-prod-2026-08-14-01
```

------------------------------------------------------------------------

# 17. Rollback Procedure

Rollback should be used if the release causes a serious application
problem and correcting it immediately is riskier than returning to the
previous known-good version.

## 17.1 Identify the Previous Good Tag

``` bash
cd /opt/jeevanam/clinic-management-platform

git tag --sort=-creatordate | head -20
```

Or use the release record.

Example:

``` text
Current:
jeevanam-prod-2026-08-15-01

Previous good:
jeevanam-prod-2026-08-14-01
```

## 17.2 Checkout the Previous Release

``` bash
git checkout jeevanam-prod-2026-08-14-01
```

Verify:

``` bash
git describe --tags --always
git log -1 --oneline
```

## 17.3 Validate Compose

``` bash
cd /opt/jeevanam/clinic-management-platform/local

docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config >/dev/null
```

## 17.4 Rebuild/Recreate

``` bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  up -d --build
```

## 17.5 Verify

Run the same:

``` text
container health checks
API health
Keycloak OIDC check
frontend checks
functional smoke test
```

------------------------------------------------------------------------

# 18. Database Migration Precautions

A source-code rollback and a database rollback are **not the same
thing**.

If the new release contains Flyway/database migrations, determine
whether the old application can safely run against the migrated schema.

Before such a deployment:

``` bash
systemctl start jeevanam-backup.service
```

Verify the backup completed successfully.

Review migration files before deployment.

For example:

``` bash
git diff \
  <previous-release>..<new-release> \
  -- backend/**/db/migration/
```

If a release performs destructive schema/data changes, define the
rollback strategy **before deployment**.

Do not automatically restore the production database merely because
application code was rolled back.

Database restoration is a separate recovery operation and can discard
legitimate data created after the backup.

------------------------------------------------------------------------

# 19. Special Cases

## 19.1 Frontend-Only Change

You can rebuild the affected frontend rather than treating every release
as a full outage.

Example:

``` bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile frontend \
  up -d --build web-admin
```

Other frontend service names:

``` text
web-care
web-discover
web-aiva
```

Then run the appropriate frontend and authentication smoke tests.

------------------------------------------------------------------------

## 19.2 API-Only Change

``` bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  up -d --build clinic-management-api
```

Then verify:

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://api.jeevanam.deepthoughtnet.com/actuator/health
```

Also perform a functional test of the changed API workflow.

------------------------------------------------------------------------

## 19.3 Keycloak Configuration Change

Keycloak changes require extra care because authentication affects the
whole platform.

After deployment:

``` bash
docker logs --tail=200 jeevanam-prod-keycloak
```

Then verify:

``` bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management/.well-known/openid-configuration
```

Finally perform a real browser login.

------------------------------------------------------------------------

## 19.4 Production Compose Change

If `local/docker-compose.prod.yml` changed, always run:

``` bash
docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config
```

Review the resolved configuration before executing `up`.

Pay particular attention to:

``` text
volumes
ports
container names
environment variables
health checks
dependencies
profiles
```

------------------------------------------------------------------------

# 20. Commands You Must Avoid

## Never casually run

``` bash
docker compose down -v
```

`-v` can remove persistent volumes.

This may destroy production data.

## Do not remove the PostgreSQL volume

Do not remove:

``` text
jeevanam_prod_postgres_data
```

## Do not delete MinIO persistent storage

Uploaded documents may be stored there.

## Do not commit the production environment file

Never run:

``` bash
git add local/.env.prod-jeevanam
```

It should remain ignored by Git.

Verify:

``` bash
git check-ignore -v local/.env.prod-jeevanam
```

## Do not manually edit production source code

Production should represent a known Git release.

Emergency changes should still be committed properly and deployed as a
new release.

------------------------------------------------------------------------

# 21. Quick Release Checklist

Use this checklist for every release.

``` text
PRE-RELEASE

[ ] Change completed
[ ] Local/UAT test passed
[ ] git status reviewed
[ ] git diff reviewed
[ ] No secrets included
[ ] Commit created
[ ] Commit pushed to GitHub
[ ] Production tag created
[ ] Production tag pushed


PRODUCTION — BEFORE CHANGE

[ ] SSH into VPS
[ ] Production backup executed
[ ] Backup completed successfully
[ ] Current Git tag recorded
[ ] Current commit recorded
[ ] git status is clean


DEPLOYMENT

[ ] git fetch origin
[ ] git fetch --tags
[ ] New tag inspected
[ ] New tag checked out
[ ] docker compose config passed
[ ] docker compose up -d --build completed


VALIDATION

[ ] Containers healthy
[ ] API health = 200
[ ] Keycloak OIDC = 200
[ ] Discover = 200
[ ] Care = 200
[ ] Healthcare = 200
[ ] AIVA = 200
[ ] Login tested
[ ] Changed functionality tested
[ ] No critical errors in logs


RELEASE COMPLETE

[ ] Release tag recorded
[ ] Commit recorded
[ ] Backup timestamp recorded
[ ] Smoke-test result recorded
[ ] Previous rollback tag recorded
```

------------------------------------------------------------------------

# 22. Quick Command Reference

This is the short operational sequence after the release has already
been tested and committed.

## Development Machine

``` bash
cd ~/code/clinic-management-platform

git status
git push origin main

git tag jeevanam-prod-YYYY-MM-DD-NN
git push origin jeevanam-prod-YYYY-MM-DD-NN
```

## Production VPS --- Backup

``` bash
systemctl start jeevanam-backup.service

journalctl \
  -u jeevanam-backup.service \
  -n 50 \
  --no-pager
```

## Production VPS --- Deploy

``` bash
cd /opt/jeevanam/clinic-management-platform

git status
git describe --tags --always
git rev-parse HEAD

git fetch origin
git fetch --tags

git checkout jeevanam-prod-YYYY-MM-DD-NN

cd local

docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  config >/dev/null

docker compose \
  --env-file .env.prod-jeevanam \
  -f docker-compose.prod.yml \
  --profile api \
  --profile frontend \
  up -d --build
```

## Production VPS --- Verify

``` bash
docker ps \
  --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

curl -sS -o /dev/null -w "API: %{http_code}\n" \
  https://api.jeevanam.deepthoughtnet.com/actuator/health

curl -sS -o /dev/null -w "Keycloak: %{http_code}\n" \
  https://auth.jeevanam.deepthoughtnet.com/auth/realms/clinic-management/.well-known/openid-configuration

curl -sS -o /dev/null -w "Discover: %{http_code}\n" \
  https://jeevanam.deepthoughtnet.com

curl -sS -o /dev/null -w "Care: %{http_code}\n" \
  https://care.jeevanam.deepthoughtnet.com

curl -sS -o /dev/null -w "Healthcare: %{http_code}\n" \
  https://health.jeevanam.deepthoughtnet.com

curl -sS -o /dev/null -w "AIVA: %{http_code}\n" \
  https://aiva.jeevanam.deepthoughtnet.com
```

------------------------------------------------------------------------

## Recommended Production Rule

> **No production change without a Git release, pre-release backup,
> deployment validation, and post-release smoke test.**

This keeps Jeevanam production reproducible and gives every deployment a
clear recovery path.
