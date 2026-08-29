# Vaccination External History Provenance and Billing

## Boundary

- Product area: Jeevanam Vaccination
- Owning bounded context: `vaccination-domain`
- API adapter: `api-bff`
- Frontend area: `web-admin/src/pages/vaccinations/VaccinationsPage.tsx`

## Scope

- Preserve recorded-by provenance for external vaccination history.
- Prevent external vaccination histories from reusing the recording user as the administered-by identity.
- Keep internal vaccination administration and billing behavior unchanged.
- Hide external billing actions in the operational history workspace while preserving legacy read-only access where a bill already exists.

## In scope

- external vaccination record mapping
- administered-by display semantics for external vs internal records
- billing action gating for external history rows
- service-level guard against creating a new bill for an external vaccination
- regression tests for external history provenance and billing visibility

## Out of scope

- vaccination master validation
- internal vaccination administration workflow
- adverse-event recording
- billing product design outside vaccination history
- database migrations unless a new persisted field becomes necessary

## Compatibility notes

- Existing external records remain readable.
- No historical data rewrite is required.
- If a legacy external record already has a bill link, the row remains readable in a read-only manner, but new billing creation is blocked.

## File ownership map

- `backend/domains/vaccination-domain/src/main/java/com/deepthoughtnet/clinic/vaccination/service/VaccinationService.java`
- `backend/domains/vaccination-domain/src/test/java/com/deepthoughtnet/clinic/vaccination/service/VaccinationServiceTest.java`
- `web-admin/src/pages/vaccinations/VaccinationsPage.tsx`
- `web-admin/test/vaccination-rbac-and-csv.test.mjs`
