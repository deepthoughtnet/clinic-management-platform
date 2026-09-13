# AIVA P1E: Semantic Precedence and Planner Gate

## Scope

Harden the existing CareAI runtime without adding a semantic layer. A new
validated `CanonicalTurn` semantic change takes precedence over stale fallback
state and missing-field prompts.

## Ownership and Placement

- Owning context: patient-portal CareAI orchestration in `api-bff`.
- Runtime files: `PatientPortalCareAiTurnInterpreter` and
  `PatientPortalCareAiService`.
- Persistence: existing conversation snapshot/context persistence; no schema
  or migration changes.
- Channels: existing text and voice adapters remain unchanged.

## Rules

1. Planner eligibility considers semantic complexity, not only missing state.
2. Fast-path interpretation is limited to unambiguous single-purpose turns.
3. `CanonicalTurn.source` reports the actual contributing source.
4. Canonical semantic changes are applied before topic/fallback interception.
5. Doctor, clinic, specialty, service, location, date, time, selection, and
   workflow changes invalidate criteria-bound fallback state.
6. Availability-first discovery reuses existing doctor and availability skills.
7. Writes remain confirmation-gated and provider capability rules remain
   unchanged.

## Validation

Focused tests cover planner eligibility, source reporting, semantic precedence,
fallback invalidation, doctor correction, availability-first discovery, topic
switching, and Hindi/Hinglish interpretation. Existing CareAI and package
builds remain required.
