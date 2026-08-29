# Vaccination RBAC Final Alignment

## Boundary

- Product area: Jeevanam Vaccination
- Frontend area: `web-admin`
- API adapter: `api-bff`
- Owning domain: `vaccination-domain`

## Scope

- Align external vaccination verification between frontend and backend for `PLATFORM_ADMIN` inside a valid tenant context.
- Restrict `VACCINE_MASTER_MANAGER` to Vaccine Master administration only.
- Keep operational vaccination workflows unchanged for the supported clinical and operational roles.
- Preserve tenant isolation and backend authority.

## In scope

- backend authorization for external verification
- frontend route and landing-page access for operational Vaccinations
- Vaccine Master route retention for the dedicated master role
- regression tests for authorization parity

## Out of scope

- redesigning the role model
- changing supported clinical workflow permissions for the existing business roles
- altering Vaccine Master validation or data model behavior
- changing billing, adverse-event, or history semantics

## Compatibility notes

- CLINIC_ADMIN, TENANT_ADMIN, DOCTOR, RECEPTIONIST, BILLING_USER, AUDITOR, and PHARMACIST retain their existing Vaccination behavior.
- `PLATFORM_ADMIN` remains tenant-scoped for vaccination verification.
- `VACCINE_MASTER_MANAGER` keeps Vaccine Master access but no longer receives a supported operational Vaccinations workspace.
