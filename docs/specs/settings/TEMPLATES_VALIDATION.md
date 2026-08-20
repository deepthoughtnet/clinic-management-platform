# Administration Templates Validation

Status: Approved implementation reference

## Scope

This specification covers the Tenant/Clinic Admin `Templates` page at `/admin/templates`.

It applies to:

- template create
- template edit
- template preview
- duplicate
- activate/deactivate
- delete
- tenant-scoped listing and filtering

## Required Fields

The following fields are mandatory for all templates:

- Name
- Type
- Channel
- Category
- Body

These fields must:

- show a required indicator in the UI
- reject blank and whitespace-only values
- validate on the frontend and backend
- return human-readable validation messages

## Channel-Aware Subject Rule

- `Subject` is required for `EMAIL` templates.
- `Subject` is optional for non-email channels unless a downstream renderer explicitly requires it.

## Optional Fields

- Description
- Variables JSON

## Validation Rules

- Name is trimmed, length-limited, and unique within the tenant/template-type scope already used by the repository.
- Description is optional and trimmed when provided.
- Body is trimmed, length-limited, and rejects blank/whitespace-only values.
- Variables JSON is optional. When provided, it must be valid JSON and must be a JSON object.
- Subject must be validated according to the selected channel.
- Template text fields reject malformed placeholder syntax such as unmatched or empty `{{...}}` tokens.
- Unknown placeholder names are preserved by the current renderer and are not blocked by this specification.

## Backend Authority

Backend validation remains authoritative. The frontend provides fast feedback, but the API enforces the final business rules and duplicate-name checks.

## Compatibility

This is a validation hardening change. Existing lifecycle behavior, tenant scoping, audit behavior, and system-template protections must remain unchanged.
