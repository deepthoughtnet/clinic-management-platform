# Notification Settings Hardening

## Boundary
Administration route: `/admin/notification-settings`

Backend API: `GET/PUT /api/admin/notification-settings`

Owned bounded context: `carepilot-domain`

Inbound adapter: `api-bff`

Frontend area: `web-admin/src/pages/admin/NotificationSettingsPage.tsx`

## Scope
- Tighten default and fallback channel selection so only enabled and ready channels are selectable.
- Preserve backend authority for routing validation.
- Keep unavailable channels visible but disabled in the UI.
- Preserve persisted invalid fallback state until the administrator corrects it.
- Add weekday-based quiet hours scheduling with optional effective date range using the existing policy JSON payload.
- Preserve existing quiet-hours suppression for non-critical notifications and bypass for critical alerts.
- Validate persisted rate-limit policy values as whole numbers greater than zero and reject malformed/overflow inputs on both UI and backend.

## Out of scope
- Notification matrix semantics.
- Consent, audit, and rate-limit behavior.
- Provider setup flows.
- Any destructive schema replacement.

## Data and compatibility
- No Flyway migration is required for this batch.
- Weekdays and optional effective dates are stored in `notificationPolicyJson`.
- Existing persisted quiet-hours values remain readable.
- Existing persisted invalid routing values must remain visible until corrected.
- Existing rate-limit policy values remain editable through the JSON policy payload.

## Validation rules
- Default channel must be enabled and ready for use.
- Fallback channel must be `None` or enabled and ready for use.
- Disabled or unconfigured channels cannot be routing destinations.
- Quiet hours require timezone, start, end, and at least one weekday when enabled.
- Overnight quiet-hours windows are valid.
- If both effective dates are supplied, `Effective From <= Effective Until`.
- Rate-limit values must be whole numbers greater than zero.
- Zero is not supported for rate-limit policy values in this implementation.
- Cross-field rate-limit coupling is not introduced because the runtime engine does not currently enforce it.

## Tests
- Backend routing validation.
- Backend quiet-hours validation and scheduling.
- Critical-alert bypass.
- Tenant isolation and RBAC.
- Frontend disabled-option rendering and validation messaging.
- Frontend and backend rate-limit validation messaging.
