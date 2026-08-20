# Users & Roles Validation Hardening

Status: Approved implementation reference

## Scope

Tenant/Clinic Admin `Users & Roles` at `/settings/users-roles`.

## Goals

- Require the correct mandatory fields for human tenant users.
- Preserve tenant scoping, audit behaviour, and Keycloak-backed identity ownership.
- Prevent raw backend property names from leaking into user-facing validation.
- Keep create and edit rules consistent across frontend and backend.

## Mandatory fields

For normal human tenant users:

- First Name / Name
- Email
- Role
- Department

## Optional fields

- Last Name
- Username / Login ID
- Employee Code
- Mobile Number
- Temporary Password

## Validation rules

- Required fields reject blank and whitespace-only values.
- Email is syntactically validated and normalized using existing auth semantics.
- Mobile Number uses the current India-style 10-digit convention when supplied.
- Username / Login ID is optional but, when supplied, must be trimmed, length-limited, and unique in the authentication identity scope.
- Employee Code is optional but, when supplied, must be unique within the tenant/clinic scope.
- Department is required for human users, but the UI should allow practical department values rather than enforcing a closed hard-coded list when no authoritative master exists.
- Role/department mismatches that are clearly suspicious must not silently pass.

## Special cases

- `SERVICE_AGENT` is treated as a non-human/service identity and must not appear in the standard human user create/edit flow.
- A clinic admin must not edit their own privileged record through direct API access.
- Backend authorization is authoritative; frontend controls are UX helpers only.

## Error contract

- User-facing validation messages must use business-friendly labels.
- Raw Java property names and constraint names must not appear in the UI.
- Duplicate identity conflicts must use the existing structured conflict response where applicable.

## Tenant isolation

- User list/edit/reset/role assignment operations must remain tenant-scoped.
- Employee-code uniqueness must remain tenant-scoped.
- Identity-provider conflicts remain authoritative for email/login ID uniqueness.

## Verification

- Add focused frontend schema tests.
- Add backend service/controller tests covering required fields, uniqueness, suspicious role/department combinations, service-agent handling, and self-edit protection.
