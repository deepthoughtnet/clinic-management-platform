# Vaccine Master Validation Hardening

## Boundary

- Product area: Jeevanam Vaccination
- Owning bounded context: `vaccination-domain`
- API adapter: `api-bff`
- Frontend area: `web-admin/src/pages/vaccinations/VaccineMasterPage.tsx`

## Scope

- Align Vaccine Master create/edit validation across frontend, backend, CSV import/export, and persistence.
- Preserve existing vaccine records, vaccination history, and operational vaccination workflows.
- Keep the dedicated admin route `/admin/vaccine-master` unchanged.

## In scope

- mandatory-field markers
- conditionally required inventory mapping and catch-up max-age fields
- Active as a defaulted persisted state, not a user-entered required field
- controlled vocabulary inputs for Vaccine Master configuration
- text length and numeric validation parity
- inventory item existence/active checks for Vaccine Master mappings
- CSV clinical-indications length parity with backend contract
- automated regression tests for the updated rules

## Out of scope

- vaccination recording workflows
- report generation/version history
- billing, receipts, and payment history
- database migrations unless a hard validation gap requires one
- navigation or page layout redesign

## Compatibility notes

- Existing stored vaccine masters remain readable.
- No historical vaccination or billing records are rewritten.
- CSV import continues to operate as a forward-only add/update workflow.

## File ownership map

- `backend/domains/vaccination-domain/src/main/java/com/deepthoughtnet/clinic/vaccination/service/VaccinationService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/vaccination/VaccineCsvService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/vaccination/dto/VaccineRequest.java`
- `frontend/packages/form-validation-kit/src/schemas/clinicOps.ts`
- `web-admin/src/pages/vaccinations/VaccineMasterPage.tsx`
