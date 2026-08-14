# Jeevanam Discover Provider Controlled-Access Authentication Mode

## Status

Approved for implementation.

## Scope

Add a Discover-owned provider authentication mode that keeps the existing provider verification-code login intact while allowing Friends & Family / controlled-preview environments to use invitation-controlled provider access.

In scope:

- configurable provider authentication mode
- ACCESS_APPROVAL login presentation for `/provider/login`
- provider access request submission
- Platform Admin review of provider access requests
- approval / rejection / revocation lifecycle
- temporary controlled login for approved provider users
- audit trail for provider access requests and lifecycle changes
- tests for mode selection, request lifecycle, security isolation, and login behavior

Out of scope:

- Care authentication changes
- Healthcare authentication
- Platform Admin authentication
- Discover public browsing
- provider onboarding / public-profile publication lifecycle changes
- provider moderation / publication workflow changes
- Keycloak configuration
- tenant architecture

## Ownership

- Owning bounded context: Discover
- Backend transport and orchestration: `backend/api/api-bff`
- Provider access-request persistence and business rules: `backend/domains/discover-domain`
- Provider login and request-access UI: `web-discover`
- Platform Admin access-request review workspace: `web-admin`

## Configuration Sources

- Backend source of truth: `CLINIC_PROVIDER_PORTAL_AUTH_MODE`
- Frontend presentation source: `VITE_PROVIDER_PORTAL_AUTH_MODE`
- Provider development code exposure: `CLINIC_PROVIDER_PORTAL_EXPOSE_DEV_OTP`
- Explicit configured values always win over localhost/dev fallback
- Default local development mode: `DEV_OTP` when no explicit frontend value is present

## Auth Modes

Supported values:

- `DEV_OTP`
- `ACCESS_APPROVAL`
- `OTP`

Behavior:

- `DEV_OTP`: existing provider verification-code login remains available for local development and controlled non-production use
- `ACCESS_APPROVAL`: provider OTP/login-code requests are disabled; provider access is granted through request/approval and controlled access-code login
- `OTP`: current verification-code-compatible login path remains available for future production use

## Required Business Rules

- verification-code request / verification must be rejected in `ACCESS_APPROVAL`
- dev OTP must not be exposed in `ACCESS_APPROVAL`
- provider login page must present request-access and approved-user sign-in paths in `ACCESS_APPROVAL`
- only `PLATFORM_ADMIN` may review provider access requests
- approval must map to an explicit provider account / workspace identity
- direct provider-account IDs must not be accepted from the request form
- duplicate requests must be rejected with a business-readable response
- audit must record request / approve / reject / activate / revoke events
- existing provider session token format should be reused

## Persistence Plan

Add a dedicated Discover table for provider access requests and controlled login state. Keep historical records auditable and enforce optimistic locking where applicable.

## Compatibility

- Existing provider verification-code tables, services, and UI remain intact
- Existing provider session token format remains the same
- Existing provider onboarding, public profile, and publication flows are unchanged
- Existing Discover public browsing remains unchanged

## Validation

- backend service and controller tests for each auth mode
- request lifecycle tests
- login isolation tests
- frontend tests for login presentation switching
- platform admin workspace tests

