# Notification Operations Empty-Data Success KPI

## Boundary
Administration route: `/admin/notification-operations`

Backend API: `GET /api/notification-operations/summary` and `GET /api/notification-operations/providers`

Owned bounded context: `carepilot-domain`

Inbound adapter: `api-bff`

Frontend area: `web-admin/src/pages/admin/NotificationOperationsPage.tsx`

## Scope
- Preserve the existing delivery success percentage calculation for non-empty datasets.
- Return a neutral no-data success state when there are zero eligible delivery attempts.
- Keep provider readiness separate from provider operational success.
- Keep empty-state tabs unchanged for Deliveries, Failures & Retries, Analytics, and Audit.

## Data and compatibility
- The summary payload may return a nullable success rate.
- Provider operational success is derived from delivered attempts and is shown as `N/A` when the denominator is zero.
- No persistence or schema change is required for this behavior.

## Validation rules
- Success percentage is only defined when the number of eligible channel delivery attempts is greater than zero.
- Zero eligible attempts must render as `N/A` with a neutral status label.
- Existing thresholds continue to apply when a real percentage exists.
- Provider readiness labels remain unchanged when there is no traffic.

## Tests
- Backend summary no-data semantics.
- Backend provider-row zero-attempt semantics.
- Frontend empty-state KPI rendering and source wiring.
