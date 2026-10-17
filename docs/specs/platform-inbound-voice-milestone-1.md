# Platform inbound voice milestone 1

## Status

Approved implementation scope for the Platform Communication Test inbound voice
transport milestone.

## Scope

Capture only the DotVoice inbound caller number and the first complete caller
utterance transcript. The context is in-memory and keyed by provider `call_id`.
The call receives one Hindi greeting, one STT pass through the existing
`VoiceOrchestratorService`, one Hindi acknowledgement, and then a provider
hangup. No patient, appointment, reminder, Engage, or AIVA business workflow is
invoked.

## Ownership and boundaries

- Voice interaction contract owner: `carepilot-domain`.
- Inbound adapter/orchestration: `api-bff` Communication Test.
- Frontend: `web-admin` Communication Test, URL-synced `INBOUND_VOICE` tab.
- Persistence/migrations: none; the Platform test context is in-memory.
- Existing DotVoice, TTS, μ-law/VAD, WAV conversion, and STT provider paths are reused.

## Provider contract

The stream receiver accepts the repository's DotVoice event contract:
`incoming_call` with `call_id`, `from_number`, `to_number`, and `vendor`; the
legacy portal shape `from`/`to` is also accepted. Subsequent `stream_start`,
`media` (base64 μ-law 8 kHz), and `stream_stop` events are correlated by
`call_id`. Incoming calls are auto-answered by DotVoice: the listener sends no
answer control frame. `stream_start` with `direction=inbound` transitions the
context from `RINGING` to `ANSWERED`. Pre-answer rejection, when needed, uses
`{"event":"reject_call","call_id":"<call_id>"}`. Media playback and
answered-call hangup use the existing stream control implementation,
`{"event":"control","call_id":"<call_id>","action":"hangup"}`.

## Compatibility and safety

Existing outbound Communication Test modes remain unchanged. Inbound contexts
are isolated by `call_id`, caller numbers are retained exactly in the authorized
Platform Admin response, and logs mask phone numbers and never contain audio,
transcripts, or credentials. No database migration or business-domain mutation
is introduced.

## Validation

- Unit tests cover event field capture, call correlation/isolation, greeting-before-listening,
  first-only STT, Sarvam result storage, acknowledgement/hangup, and no business mutation.
- DotVoice stream tests cover inbound auto-answer/media/stream-start correlation,
  field-shape fallback, rejection/hangup contracts, and simultaneous calls.
- API module tests, domain tests, web-admin tests/build, and architecture validation are required.
