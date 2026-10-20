# IR-02: Inbound DotVoice DID tenant routing

## Scope

Resolve the called DotVoice DID at `incoming_call` through the persisted
`CommunicationChannelResolver` and attach the resolved tenant/clinic/location
metadata to the existing provider-call-id keyed Communication Test context.

## Out of scope

Patient identification, AIVA, appointments, booking, Discover voice, Engage
interactive workflows, and changes to the persistent DotVoice WebSocket or
STT/TTS/VAD implementations.

## Invariants

- Routing is performed before any future tenant-sensitive business lookup.
- Context is isolated by `providerCallId`; no global tenant request context is
  written from the long-lived provider WebSocket.
- Unknown, disabled, or inbound-disabled DIDs are marked as routing failures
  and do not enter the existing greeting/transcript flow.
- Resolver fallback behavior remains owned by `CommunicationChannelResolver`.
