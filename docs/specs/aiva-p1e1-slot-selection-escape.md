# AIVA P1E.1 Slot-Selection Escape

## Ownership

- Owning context: Patient Portal CareAI orchestration
- API module: `backend/api/api-bff`
- Persistence: existing CareAI conversation state; no schema change
- Frontend: unchanged

## Contract

CanonicalTurn semantics are applied before slot-selection controls. A slot
selection prompt may only handle pagination/rerender controls or an actual
slot selection. A date, time, doctor, specialty, workflow, or context-question
turn must be handled by its semantic path first.

Slot candidates are invalidated when their effective doctor, date, time, or
provider-discovery criteria change. Alternative-doctor requests also clear the
requested doctor name so stale provider lookup cannot reselect the previous
doctor.

Availability-first provider requests continue through the existing discovery
and availability skills without changing provider visibility or booking rules.

## Planned files

- `PatientPortalCareAiService.java`: semantic precedence and stale candidate
  invalidation
- `PatientPortalCareAiServiceTest.java`: slot escape, context question,
  alternative doctor, and availability-first regressions

## Validation

Run focused CareAI tests, the full CareAI test package, backend package build,
and `git diff --check`.
