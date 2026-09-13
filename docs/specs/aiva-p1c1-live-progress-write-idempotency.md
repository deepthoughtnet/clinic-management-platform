# AIVA P1C.1: Live Progress and Mutation Idempotency

## Scope

Close two runtime gaps without changing the CareAI workflow model or voice provider:

- expose one safe `turn.progress` websocket event when a deterministic skill remains
  running after the existing 700 ms waiting threshold;
- apply the existing durable idempotency store to cancel and reschedule mutations,
  including timeout reconciliation through the existing appointment state lookup.

## Ownership and placement

- Owning context: Patient Portal CareAI and patient voice adapter.
- API module: `backend/api/api-bff`.
- Persistence owner: existing reliability idempotency service; no migration.
- Frontend area: `web-care` voice session diagnostics only if required.

The CareAI service remains the authoritative state mutation boundary. The websocket
adapter receives only safe semantic progress data and maps it to transport events.
Skills do not own conversation state.

## Non-goals

No STT/TTS change, websocket protocol redesign, provider/entitlement change,
multi-agent architecture, or booking policy change.
