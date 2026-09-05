# Platform Ops Operations Control Center - Phase 1A

api_module: api-bff
frontend_module: web-admin
status: approved

## Context

The existing `/platform/operations` workspace is the platform admin operations surface. It already serves alert, queue, provider, scheduler, runtime, and dead-letter observability. Phase 1A extends that page with a compact production health matrix and lightweight infrastructure/release visibility without creating a new observability subsystem.

## Phase 1A scope

- resilient page loading with partial failure isolation
- normalized component-health DTOs
- new platform overview endpoint
- health matrix for API, DB, Redis, Keycloak, MinIO, Gemini, Groq, Document AI, Scheduler, Backups, and Release
- passive AI health should reuse the existing AI invocation and document extraction telemetry instead of introducing a parallel observability source
- Platform Ops may provide drill-down links to `/platform/ai-ops` and related detail screens, but it must not duplicate those pages
- release/build metadata from runtime configuration
- lightweight health probes for API, PostgreSQL, Redis, Keycloak, and MinIO
- scheduler health derived from current in-memory telemetry
- backup state reported as UNKNOWN until structured telemetry exists

## Non-goals

- backup ingestion or shell/systemd execution from requests
- durable scheduler-heartbeat persistence
- detailed Gemini/Groq/Document AI telemetry
- Docker/container health
- host resource metrics
- SSL expiry
- user/session analytics

## Backend contract

- Additive overview endpoint under the platform namespace.
- Existing tenant-scoped `/api/ops/**` endpoints remain in place.
- Platform overview must be usable without a clinic tenant selected.
- Component failures must be isolated in the response payload.

## Frontend contract

- The current Platform Ops page remains the single page for platform operations.
- The new matrix appears above the existing summary cards.
- Existing alert, provider, queue, scheduler, runtime, and DLQ sections remain on the page.
- Refresh must preserve successful sections when one request fails.

## Files expected in this phase

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ops/*`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ops/dto/PlatformOpsDtos.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ApiBffApplication.java`
- `backend/api/api-bff/src/main/resources/application.yml`
- `backend/api/api-bff/Dockerfile`
- `local/docker-compose.yml`
- `local/docker-compose.prod.yml`
- `local/docker-compose.uat.yml`
- `web-admin/src/pages/admin/PlatformOpsPage.tsx`
- `web-admin/src/api/clinicApi.ts`
