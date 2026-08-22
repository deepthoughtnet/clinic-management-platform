# Platform Tenant Provisioning Registration Number Alignment

Status: Approved implementation reference

## Scope

This specification covers the Platform Admin tenant creation flow at `/platform/tenants`.

## Problem Statement

The Platform Admin Create Tenant flow provisions a tenant and then bootstraps the clinic profile. Clinic profile bootstrap requires a registration number, but the Create Tenant UI and API payload omitted that field and the backend mapped it to `null`.

## Required Behavior

The create-tenant contract must collect and transmit every field required by clinic-profile bootstrap, including:

- Clinic Name
- Tenant Code
- Tenant Name
- Plan
- City
- State
- Country
- Clinic Email
- Phone
- Address Line 1
- Registration Number
- Clinic Admin email
- Clinic Admin first name
- Clinic Admin last name

## Validation Rules

- Registration Number is required.
- Registration Number must reject blank and whitespace-only values.
- Registration Number must be trimmed before submission.
- Registration Number must remain business-friendly and length-limited.
- Backend validation remains authoritative.

## Compatibility Rules

- Do not weaken clinic-profile validation.
- Do not make Registration Number optional in clinic-profile bootstrap.
- Do not introduce hidden defaults for business-required clinic-profile fields.
- Existing tenant code and duplicate checks remain unchanged.

## Testing Expectations

- Create Tenant form exposes Registration Number as a required field.
- The request payload includes Registration Number.
- Backend tenant provisioning maps Registration Number into clinic profile bootstrap.
- Tenant creation succeeds when all mandatory fields are provided.
- Missing Registration Number fails fast with a human-readable validation message.
