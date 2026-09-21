# AIVA V2 Distributed Conversation State

## Status

Approved implementation scope for multi-instance AIVA V2 typed-text session state.

## Ownership and placement

Conversation/session orchestration remains owned by the AIVA V2 API BFF adapter.
Redis is an existing platform dependency and is used as the authoritative,
short-lived session store; no healthcare-domain persistence or Flyway migration
is introduced. Voice transport remains out of scope.

## Contract

The Redis key is `aiva:v2:session:{tenantId|patientId|conversationId}`. One
versioned envelope contains the session projection, presentation metadata,
absolute expiry, bounded turn receipts, and an optional leased reservation.

Turn admission is atomic in Redis Lua. A reservation is held only while the
semantic/domain processing runs outside Redis. Finalization is a conditional
Lua compare-and-set on the reservation token. A reservation lease permits a
same-client-turn retry to recover after a process crash, while blocking later
sequences until recovery or expiry.

Session expiry remains fixed at creation plus 30 minutes. Redis writes preserve
the original absolute expiry and never refresh the TTL.

## Configuration and migration

`clinic.ai.aiva-v2.session-store=redis` is the production/UAT mode. Local tests
and explicitly configured local development may use `memory`; production does
not silently fall back when Redis is unavailable. Existing JVM-local sessions
are allowed to expire during rollout; no unsafe in-memory migration is attempted.

## Privacy

Only workflow facts already required for continuation and bounded response
replay are serialized. Raw user turns, transcripts, confirmation secrets, and
serialized payloads are not logged or added to the envelope.

## Validation

Test coverage includes cross-instance continuation, duplicate admission,
sequence ordering, fixed expiry, reservation recovery, corrupt payload safety,
tenant/patient isolation, and existing AIVA regressions.
