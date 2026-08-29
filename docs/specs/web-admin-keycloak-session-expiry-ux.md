# Web Admin Keycloak Session Expiry UX

## Boundary

- Product area: Jeevanam Healthcare Admin
- Frontend area: `web-admin`
- Auth source of truth: Keycloak

## Scope

- Preserve current Keycloak session and refresh behavior.
- Distinguish explicit logout from access-token or refresh-token failure.
- Show a clean session-expiry message on the login screen after refresh failure.
- Add safe diagnostics for token expiry and refresh failure.
- Keep unsaved form behavior unchanged except for existing route/page safeguards.

## In scope

- `web-admin` auth bootstrap and refresh handling
- login-page expiry messaging
- safe console logging for auth lifecycle transitions
- regression tests for message and refresh-path wiring

## Out of scope

- Increasing Keycloak lifetimes
- changing refresh-token rotation policy
- altering backend auth/session models
- adding autosave for forms
- modifying provider/patient portal auth architecture

## Compatibility notes

- Existing Keycloak configuration remains authoritative.
- Explicit logout still clears the session without an expiry warning.
- Expired sessions redirect to login instead of failing with a raw API error.
