# Clinic Profile Validation

Status: Approved implementation reference

## Scope

This specification covers the Tenant/Clinic Admin `Clinic Profile` page at `/settings/clinic-profile`.

## Required Fields

The following fields are mandatory:

- Clinic Name
- Display Name
- Phone
- Email
- Address Line 1
- City
- State
- Country
- Postal Code
- Registration Number

All mandatory fields:

- show a required indicator in the UI
- reject blank and whitespace-only values
- validate on the frontend and backend
- return human-readable validation messages

## Optional Fields

- Address Line 2
- GST Number
- Public Slug

## Validation Rules

- Clinic Name and Display Name are trimmed, length-limited, and reject control characters.
- Phone is validated as the platform's supported Indian mobile format.
- Email must be syntactically valid.
- Postal Code uses India PIN code validation when Country is India; otherwise it uses a generic postal-code format check.
- GST Number is optional but must be a valid GSTIN when present.
- Public Slug is optional. When blank, the system auto-generates one from the clinic display name/clinic name. Manually supplied slugs must be URL-safe and unique.

## Backend Authority

Backend validation remains authoritative. The frontend provides fast feedback, but the API enforces the final business rules and slug uniqueness.
