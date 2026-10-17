# Engage voice response capture

## Status

Approved implementation scope for the first reusable DotVoice response-capture increment.

## Scope

Extend the existing CarePilot/DotVoice call path with a provider-neutral, deterministic
confirmation interpreter and server-side call correlation contracts. The first increment is
safe for Platform Communication Test and does not mutate appointments or reminders.

## Ownership and boundaries

- Owner: `carepilot-domain` (voice interaction contracts and call correlation).
- Inbound adapter: existing `api-bff` Communication Test controller/service.
- Frontend: existing `web-admin` Communication Test page.
- Persistence: reuse existing AI-call execution, transcript, and event records before adding
  schema columns.
- DotVoice remains the only telephony provider; existing STT/TTS SPIs remain the provider
  boundaries.

## Safety

`NO` is a patient negative intent, not a no-answer outcome. `UNCLEAR` and `NO_RESPONSE` never
perform destructive business actions. Platform test mode only reports the action that would be
executed. Conversation context is keyed by server-correlated provider call ID and must never
derive tenant or patient identity from WebSocket payloads.

## Interactive completion milestone

Platform Communication Test processes a bounded caller utterance during an active DotVoice
stream using the existing AIVA-configured speech threshold and silence timeout. Prompt,
reprompt, and closing playback are protected from STT by a per-call bot-speaking guard. After
closing playback completes, DotVoice control hangup is requested, with the existing REST call
API available as an idempotent fallback. No appointment, reminder, campaign, or patient
record is mutated by this milestone.
