# Healthcare User Login Identity Uniqueness

## Purpose

Ensure staff authentication identifiers remain globally unique across the Jeevanam Healthcare platform while preserving tenant-scoped membership records.

## Scope

- Healthcare tenant user create/update flows
- Keycloak-backed authentication identity provisioning
- Username / Login ID uniqueness
- Authentication email uniqueness
- Frontend conflict messaging in Users & Roles

## Architecture

- `app_users` remains a tenant membership projection and is not the auth identity source of truth.
- Keycloak is the authoritative identity boundary for login ID and authentication email uniqueness.
- Employee code remains tenant-scoped.
- Mobile number uniqueness is unchanged.

## Rules

- Username / Login ID must be normalized by trim + case-insensitive comparison.
- Authentication email must be normalized using the platform's existing auth semantics (trim + lowercase).
- Create and edit flows must reject conflicts with a clean 409 business error.
- Current identity updates must be allowed when username/email are unchanged.
- The implementation must preserve legitimate one-platform-identity-to-many-tenant-membership behavior.

## Error contract

- Conflicts are returned as a structured 409 response with one or more field entries.
- Username conflict: `Login ID already in use.`
- Email conflict: `This email address is already associated with a Jeevanam account.`
- When both identifiers conflict, both field errors are returned together.

## Validation

- Backend enforcement is authoritative.
- Frontend should surface the backend conflict inline in Users & Roles without exposing internal IDs or raw Keycloak/database errors.
