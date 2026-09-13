# AIVA Voice Excellence Stage F

## Status

Approved implementation note for Stage F.

## Scope

Improve the patient portal voice experience without redesigning AIVA business workflows.

This stage hardens the existing voice stack for:

- low-latency conversational turn handling
- continuous listening after assistant playback
- barge-in interruption
- explicit voice session state transitions
- safe latency telemetry for UAT and diagnostics
- echo/self-transcription prevention

## Constraints

- Keep the single AIVA orchestrator.
- Keep existing deterministic resolvers, typed skills, workflow sub-states, confirmation, and authorization rules.
- Do not change Provider, Discover, entitlement, booking rules, or other business workflows.
- Do not add multi-agent orchestration.
- Do not change the transport contract unless a narrow voice lifecycle defect requires it.
- Do not add unrelated product capabilities.

## In scope

- Patient portal voice session state handling in `web-care`
- Realtime patient voice websocket contract validation in `api-bff`
- Continuous microphone resume after assistant playback
- Barge-in interruption and recovery behavior
- Safe telemetry for:
  - speech timing
  - transcript timing
  - orchestration timing
  - TTS timing
  - playback timing
  - barge-in latency
- Regression tests for continuous conversation, barge-in, mute, end, reconnect, duplicate events, and unsupported playback cases

## Out of scope

- Booking workflow redesign
- New care workflows
- Provider / Discover rule changes
- STT/TTS provider replacement
- Voice gateway architecture rewrite
- Multi-agent orchestration

## Implementation plan

1. Document the current voice lifecycle and state machine in the patient portal voice shell.
2. Keep the microphone stream available for interruption detection while assistant audio is playing.
3. Distinguish recorder lifecycle from stream lifecycle so continuous listening can resume without a full reconnect.
4. Add barge-in handling that stops assistant playback and resumes user capture safely.
5. Expose safe telemetry fields for UAT diagnostics.
6. Add regression tests for continuous turns, barge-in, mute/unmute, end, and websocket event ordering.

## Validation

- `npm --prefix web-care run test`
- `npm --prefix web-care run build`
- Backend/gateway tests if the websocket contract is changed

