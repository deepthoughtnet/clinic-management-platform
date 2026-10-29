# AIVA V2 Voice slot-page continuation normalization

Status: approved by the local-only slot-continuation repair request.

## Scope and ownership

The existing AIVA Voice adapter in `api-bff` owns contextual continuation
normalization. Appointment services retain availability and mutation ownership.
No persistence, migration, frontend, provider adoption, or authentication changes
are required. Dependencies remain within the existing adapter/kernel boundary.

## Contract

In a collecting BOOKING workflow with current displayed canonical slot choices,
bounded Hindi, Hinglish, and English next/more-slot controls normalize before
provider extraction to SHOW_MORE_SLOTS / REQUEST_ALTERNATIVE. In this context,
slot/slots and the likely STT substitutions plot/प्लॉट are accepted. Explicit
doctor syntax and date/time refinements do not match the bounded control grammar.
Outside that context the ordinary language adapter remains authoritative.

SessionProjection.latestAvailabilityResult retains the canonical slots, request
identity, filters, and displayOffset/pageSize. The existing kernel alone advances
the page. No availability reload or booking draft reset is introduced.

## File placement and validation

Modify AivaV2VoiceConversationAdapter and the existing DotVoice inbound-path
test harness. Exercise the exact multi-turn evening-page sequence and STT variant,
assert unchanged provider/date/draft/request/fingerprint/slot references and
offset increments, and verify no discovery/availability/AI/mutation call on the
pagination turns. Protect out-of-context and explicit-doctor behavior. Run the
relevant adapter/kernel/language/inbound suites, wiring/architecture checks,
broader AIVA suite, and git diff --check. Do not rebuild Docker.
