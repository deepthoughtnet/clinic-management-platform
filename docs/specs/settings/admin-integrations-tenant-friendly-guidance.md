# Admin Integrations Tenant-Friendly Guidance

## Boundary
Administration route: `/admin/integrations`

Backend API: `GET /api/admin/integrations/status`

Owned bounded context: `carepilot-domain`

Inbound adapter: `api-bff`

Frontend area: `web-admin/src/pages/admin/IntegrationsPage.tsx`

## Scope
- Preserve readiness and test-send semantics for integrations.
- Replace raw internal configuration keys with business-friendly guidance in the Clinic Admin experience.
- Keep technical configuration details available only to platform/internal technical roles.
- Preserve navigation and existing integration status categories.

## Data and compatibility
- No secret values are returned by the integrations status API.
- Business guidance is returned for all allowed roles.
- Technical configuration keys are only included when the caller is a platform/internal technical role.

## Validation rules
- READY remains configured and usable.
- DISABLED remains intentionally off.
- NOT CONFIGURED remains incomplete setup.
- FUTURE remains planned and non-actionable.
- Clinic Admin must not see raw env/property keys in the standard view.

## Tests
- Backend role-gated integrations status coverage.
- Frontend clinic-admin and platform-admin rendering coverage.
- Secret exposure regression checks.
