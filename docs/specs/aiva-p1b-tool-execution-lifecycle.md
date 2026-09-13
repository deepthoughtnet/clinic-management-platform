# AIVA P1B: Tool Execution Lifecycle and Stale Result Protection

## Owning Context

CareAI orchestration in `backend/api/api-bff`. Existing patient, appointment,
availability, discovery, authorization, and booking services remain authoritative.

## Placement

- Owning domain: CareAI application orchestration
- API module: `backend/api/api-bff`
- Persistence module: none
- Migration owner: none
- Frontend area: none
- Allowed dependencies: existing CareAI registries, workflow state, patient portal services, and platform logging

## Scope

- explicit skill execution lifecycle outcomes
- `WAITING_FOR_TOOL` workflow state and pending-skill metadata
- deterministic execution identity and stale-result suppression
- logical cancellation of read-only searches
- bounded waiting/progress policy without LLM-generated filler
- timeout and temporary-unavailability distinction
- fallback-policy decision boundary for lifecycle outcomes

## Non-Goals

- new agents or orchestrators
- appointment semantics, confirmation policy, or authorization-rule changes
- Provider/Discover visibility changes
- STT, TTS, websocket, or voice latency changes
- schema, migration, or new persistence tables

## Safety Rules

Skill results may update state only when their conversation, turn, and execution
identity are still active. Stale read results are discarded. Committed writes are
not physically cancelled; retries remain confirmation- and idempotency-gated.

`NO_MATCH`, `TEMPORARILY_UNAVAILABLE`, `TIMEOUT`, and `FAILED` are distinct
outcomes and must not be converted into one another.

## Validation

Focused tests cover outcome semantics, waiting-state transitions, stale-result
suppression, logical cancellation, timeout handling, CALL_TO_BOOK behavior, and
P0/P1A regressions. The backend package and Stage E certified corpus remain gates.
