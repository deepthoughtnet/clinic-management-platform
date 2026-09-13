# AIVA P1C: Async Progress and Write Reconciliation

## Owning context

CareAI orchestration in `backend/api/api-bff`. No persistence schema or API endpoint changes are required for the lifecycle contracts in this stage.

## Scope

- asynchronous read-only skill execution with one delayed progress event
- execution identity and stale-result suppression
- typed write idempotency keys for booking
- timeout reconciliation policy for confirmed writes
- deterministic lifecycle and policy tests

## Safety rules

- progress is `RUNNING`, never success
- a result may be applied only when its conversation, turn, and execution identity remain current
- read-only work may be logically cancelled
- write work is never blindly retried after timeout
- unknown write outcomes remain pending reconciliation

## Non-goals

- new agents or workflows
- Provider, Discover, entitlement, STT, TTS, or business-rule changes
- broad websocket protocol redesign

## Known integration boundary

The current voice request handler waits synchronously for the complete CareAI response. The async coordinator and progress event contract are therefore adapter-ready and independently tested, but websocket `turn.progress` delivery requires a later handler-level request lifecycle change.
