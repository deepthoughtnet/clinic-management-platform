# AIVA V2 DotVoice Inbound Concurrency Hardening

## Scope and placement

This batch changes only inbound scheduling, per-call transport state, bounded
backpressure, WebSocket write sequencing, and concurrency diagnostics. The API
BFF owns call setup and turn orchestration. `carepilot-domain` owns DotVoice
transport and playback correlation. No persistence, migration, frontend, or
business-domain change is required.

Allowed dependencies remain API BFF → existing Voice/application services and
carepilot-domain → Voice SPI. The implementation must not introduce a global
call lock or alter Redis session reservations, patient duplicate locking,
booking authorization, appointment locking, final confirmation, stale-slot
validation, idempotency, provider behavior, or alternate/shared Voice routes.

## Required behavior

- Timer threads perform only timer bookkeeping and enqueue completed turns.
- A bounded worker pool handles inbound routing, greeting, audio conversion,
  STT, AIVA, TTS, and playback setup. Defaults: core 4, max 8, queue 16.
- Work is serialized per provider call ID; different call IDs can run
  concurrently. Queue rejection is logged and never runs provider work on the
  WebSocket callback thread.
- Incoming-call setup precedes stream-start handling for the same call.
- Mutable playback state is per call. Sends on the shared WebSocket are
  serialized through one writer with bounded active-playback admission and scheduled frame by frame
  to permit fair multiplexing while preserving each call's frame order.
- Executors have application shutdown lifecycle and per-call state is removed
  at call termination.
- Diagnostics include call ID, turn/playback ID, stage, thread, queue wait, and
  elapsed time; no raw caller number, transcript, or patient data is added.

## Verification gate

Deterministic latch tests must prove cross-call overlap, same-call sequencing,
callback return while setup is blocked, per-call playback isolation, and frame
order. Focused Voice regressions and the complete established Phase 8 selector
must show no failure identity beyond the ten recorded baseline failures.
Automated results do not certify a physical call: re-certify one single call
before attempting two concurrent physical calls.
