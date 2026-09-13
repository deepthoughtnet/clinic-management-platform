# AIVA P1A: Availability Fallback and Global Conversation Controls

## Owning Context

CareAI orchestration in `api-bff`. Existing patient, appointment, and discovery
services remain authoritative for data, authorization, booking, and provider
capabilities.

## Scope

- deterministic no-slot fallback for online-bookable providers
- explicit `another date`, `another time`, and `another doctor` actions
- no-match anti-loop protection
- conversational abandon/farewell controls
- preservation of confirmation and CALL_TO_BOOK safety rules

## Non-Goals

- voice transport, STT, TTS, or latency changes
- new appointment semantics or provider visibility rules
- multi-agent orchestration
- new persistence tables or migrations

## Behavior

Availability `NO_MATCH` must transition to a deterministic fallback rather than
repeat the same prompt. Date, time, and doctor changes preserve the other known
criteria unless the user explicitly changes them. `CALL_TO_BOOK` providers do
not invoke availability lookup.

Farewell and conversational abandonment cancel the active unconfirmed workflow
without cancelling an existing appointment. Explicit appointment cancellation
continues through the existing confirmation-gated workflow.

## Validation

Focused tests cover fallback actions, anti-loop behavior, stale-context reset,
farewell precedence, confirmation safety, and CALL_TO_BOOK handling. Existing
Stage E certification and the backend package build remain required gates.
