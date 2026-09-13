# AIVA P1C.2: Audible Progress and Final-Result Race

## Scope

Make the existing semantic `turn.progress` event audible during slow read-only
CareAI skill execution, without introducing a second voice or business
orchestration path.

## Design

- CareAI continues to emit semantic progress only: `progressKey`, `skillId`,
  `turnId`, `skillExecutionId`, and `WAITING_FOR_TOOL`.
- The existing voice assistant TTS abstraction synthesizes the short progress
  acknowledgement using the configured provider and voice.
- The websocket adds progress audio chunk/end events alongside `turn.progress`.
- The browser keeps progress audio separate from final assistant audio.
- If the final result is ready before progress playback starts, progress is
  suppressed. If progress is already playing, final audio is queued behind it.
- Barge-in, abandonment, stale execution checks, and existing idempotent write
  handling remain authoritative.

## Timing

Progress is eligible once per skill execution at the existing 700 ms threshold.
No progress audio is generated for faster executions. An opt-in development/UAT
delay (`AIVA_CAREAI_PROGRESS_TEST_DELAY_MS`) can delay read-only doctor and
availability lookups; it is disabled by default.

## Boundaries

- No STT, TTS provider, booking, entitlement, Provider, Discover, or websocket
  protocol redesign.
- Progress is ephemeral and is not persisted as a normal CareAI answer.
- Final business results remain authoritative; progress never represents
  success.

## Verification

- Backend voice websocket and execution lifecycle tests cover progress audio,
  event ordering, and safe payloads.
- Web Care tests cover progress handling, suppression/queueing, and diagnostics.
- Live browser speaker verification remains a UAT step because automated tests
  cannot verify physical audio output.
