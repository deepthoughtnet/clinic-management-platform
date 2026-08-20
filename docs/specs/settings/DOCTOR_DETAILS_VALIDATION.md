# Doctor Details Validation Hardening

Status: Approved implementation reference

## Scope

Tenant/Clinic Admin Doctor Details at `/doctors/{doctorUserId}`.

## Goals

- Require complete doctor profile data before public listing.
- Replace editable age with date of birth as the authoritative input.
- Keep doctor-user linkage, tenant scoping, audit, and Discover integration intact.
- Reuse the existing public-slug rule pattern used by Clinic Profile.
- Prevent raw backend property names from leaking into user-facing validation.

## Mandatory fields

For Doctor Details:

- Mobile
- Specialization
- Qualification
- Registration Number
- OPD Fee
- Follow-up Fee
- Emergency Fee
- Years of Experience
- Date of Birth

## Optional fields

- Profile Photo
- Consultation Room/Location
- Public Slug

## Validation rules

- Mobile uses the platform's supported Indian 10-digit format when supplied.
- Specialization requires at least one selected value.
- Qualification, Registration Number, and Consultation Room are trimmed and length-limited.
- Public Slug is optional; when blank it is auto-generated from the doctor display name using the same slugify/collision pattern as Clinic Profile.
- Public Slug conflicts are rejected with a business-friendly message.
- Date of Birth must be a valid past date, not future dated, and must fall within plausible doctor-age bounds.
- Age is derived from Date of Birth for display and public-listing completeness checks.
- Public listing is blocked until all mandatory profile fields are valid.
- Fees are required non-negative monetary values with sensible precision and bounds.

## Backend authority

Backend validation remains authoritative. Frontend validation provides fast feedback only.

## Migration and compatibility

- Add `doctor_profiles.date_of_birth` with a forward-only migration.
- Preserve existing `age` data for legacy records.
- Do not invent DOB values for old records that cannot be derived safely.
- Keep the existing `age` column until a future compatibility review explicitly removes it.

## Verification

- Add frontend schema tests for required fields, DOB, fees, and slug validation.
- Add backend service/controller tests for required fields, DOB, derived age, slug uniqueness, and public-listing completeness.
