# AIVA Controlled Runtime Simplification

## Status

Phase A/B only. The reducer and action decision models are introduced in shadow
mode. The existing `PatientPortalCareAiService` booking runtime remains the
runtime authority until UAT approval enables the cutover.

## Ownership and Placement

- Owning bounded context: Patient Portal CareAI application orchestration.
- API module: `backend/api/api-bff`.
- Persistence owner: existing CareAI conversation persistence; this phase adds
  no schema or migration.
- Frontend area: none.
- Allowed dependencies: CanonicalTurn, existing resolvers, workflow enums,
  typed skill outcomes. Reducer and action decider have no I/O dependencies.

## Target Boundary

`Transcript -> TurnInterpreter -> CanonicalTurn -> validated resolvers ->
ConversationStateReducer -> ActionDecision -> skill executor -> structured
result -> ConversationStateReducer -> response composer`

The reducer is pure and owns the proposed booking-state projection. The action
decider is pure and returns exactly one next action. Neither performs I/O,
parses raw text, calls Gemini, or renders a response.

## Controlled Migration

1. Introduce pure booking state, reducer, action, and decision models.
2. Run them beside the existing runtime and emit safe old/new decision traces.
3. Compare decisions in focused tests and UAT diagnostics.
4. Enable the booking cutover only after the required UAT gate passes.
5. Remove obsolete booking authorities after live validation. Cancel,
   reschedule, and discovery remain on their existing paths until separately
   migrated.

The temporary environment flag is
`AIVA_CAREAI_REDUCER_BOOKING_ENABLED`, defaulting to `false`. Shadow tracing is
controlled by `AIVA_CAREAI_REDUCER_BOOKING_SHADOW`, defaulting to `true` for
booking turns. No production behavior changes in Phase A/B.

## Safety Invariants

- Only validated resolver facts enter reducer state.
- Candidate context is tied to criteria and is invalidated on criteria change.
- A positive confirmation can produce `EXECUTE_PENDING_ACTION` only when a
  pending action and valid confirmation state already exist.
- Slot selection is an action decision; the reducer never executes a skill.
- Old skill results and writes remain governed by existing execution identity,
  stale-result, authorization, confirmation, and idempotency mechanisms.
