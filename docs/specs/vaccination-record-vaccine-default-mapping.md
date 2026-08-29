# Vaccination Record Vaccine Default Mapping

## Boundary

- Product area: Jeevanam Vaccination
- Frontend area: `web-admin/src/pages/vaccinations/VaccinationsPage.tsx`
- API source of truth: vaccination master and recommendation payloads

## Scope

- Populate Record Vaccination defaults from selected Vaccine Master and recommendation data.
- Keep optional fields editable after auto-population.
- Avoid stale field carryover when the selected vaccine changes.

## In scope

- record-vaccination vaccine selection handler
- recommendation selection handler
- default price, dose number, route, administration site mapping
- regression tests for empty and switching scenarios

## Out of scope

- Vaccine Master validation
- backend validation or schema changes
- unrelated vaccination workflows

## Compatibility notes

- Existing master values remain authoritative when present.
- If a selected vaccine omits a field, the corresponding form field is cleared rather than preserving the previous vaccine's value.
