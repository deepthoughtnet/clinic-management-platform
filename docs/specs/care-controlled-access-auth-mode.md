# Jeevanam Care Controlled-Access Authentication Mode

## Status

Approved for implementation.

## Scope

Add a Care-only authentication mode that keeps the existing OTP implementation intact while allowing the Friends & Family preview environment to use invitation-controlled access.

In scope:

- configurable Care authentication mode
- ACCESS_APPROVAL login presentation for `/patient/login`
- Care access request submission
- Platform Admin review of Care access requests
- approval / rejection / revocation lifecycle
- temporary controlled login for approved Care users
- audit trail for access requests and lifecycle changes
- tests for mode, request lifecycle, and security isolation

Out of scope:

- Healthcare authentication
- Platform Admin login
- Discover
- Connect / provider onboarding
- patient clinical workflows
- booking, billing, lab, pharmacy, Engage
- Keycloak configuration
- tenant architecture

## Ownership

- Backend transport and orchestration: `backend/api/api-bff`
- Patient access-request persistence and business rules: `backend/domains/patient-domain`
- Care login and request-access UI: `web-care`
- Platform Admin access-request review workspace: `web-admin`

## Configuration Sources

- Backend source of truth: `CLINIC_PATIENT_PORTAL_AUTH_MODE`
- Frontend presentation source: `VITE_PATIENT_PORTAL_AUTH_MODE`
- Explicit configured values always win over localhost/dev fallback
- Default local development mode: `DEV_OTP` when no explicit frontend value is present

## Auth Modes

Supported values:

- `DEV_OTP`
- `ACCESS_APPROVAL`
- `OTP`

Behavior:

- `DEV_OTP`: existing development OTP behavior remains available
- `ACCESS_APPROVAL`: OTP and dev OTP are disabled; Care uses request-access and controlled login
- `OTP`: current OTP-compatible path remains available for later production use

## Required Business Rules

- OTP request / verification must be rejected in `ACCESS_APPROVAL`
- dev OTP must not be exposed in `ACCESS_APPROVAL`
- Care login page must present request-access and approved-user sign-in paths in `ACCESS_APPROVAL`
- only `PLATFORM_ADMIN` may review Care access requests
- approval must map to an explicit tenant and patient identity
- direct patient IDs must not be accepted from the request form
- duplicate requests must be rejected with a business-readable response
- audit must record request / approve / reject / activate / revoke events
- existing patient portal session token format should be reused

## Persistence Plan

Add a dedicated patient-domain table for access requests and controlled login state. Keep historical records immutable and enforce optimistic locking.

## Compatibility

- Existing OTP tables, services, and UI remain intact
- Existing Care session token format remains the same
- Existing Healthcare / Discover / Connect flows are unchanged

## Validation

- backend service and controller tests for each auth mode
- request lifecycle tests
- login isolation tests
- frontend tests for login presentation switching
- platform admin workspace tests
