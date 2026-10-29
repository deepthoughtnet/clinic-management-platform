# AIVA V2 Voice Guest Booking Preparation — Phase 2B.1

## Scope

Bridge a valid, call-bound `GUEST_BOOK_APPOINTMENT` grant into the existing AIVA V2 booking draft and canonical availability/slot-selection path. Preparation can resolve the provider, date/time constraints, availability result, and canonical `slotReference`, then persist a non-executable `PREPARED` draft in the existing `SessionProjection`.

This phase does not execute booking, confirmation, appointment lookup, cancellation, or rescheduling. A positive caller confirmation cannot reach `PatientPortalService.bookAppointment` or an appointment mutation service.

## Placement and dependencies

- `appointment-domain` continues to own canonical availability and mutation behavior; `patient-domain` continues to own guest patient creation. Neither domain gains Voice-specific persistence or a second slot engine.
- `api-bff` owns the Voice orchestration and the preparation-only entry to the existing V2 transactional kernel.
- The existing Redis-backed V2 `SessionProjection` remains the only Voice workflow store and retains `VoiceGuestRegistrationState`, `BookingDraft`, and canonical `AvailabilityResult` together.
- The preparation boundary accepts explicit Voice execution context and enforces guest capability scope/bindings. It does not synthesize Portal authentication, `SecurityContext`, or `RequestContextHolder`.
- Existing Web V2 booking behavior remains on its current kernel path.
- No migration or frontend change is included.

## Security and state invariants

- A guest remains `GUEST_REGISTERED` and unauthenticated. The grant is limited to booking preparation and remains tenant/call/session/interaction/expiry bound.
- Draft creation uses the newly created patient subject only as the booking draft subject; it does not promote that patient to an authenticated identity.
- Provider, date, daypart, exact-time/range constraints, availability fingerprint/revision, slot selection, and `slotReference` use the existing V2 kernel/tools and canonical appointment-domain result.
- Preparation stores a `PREPARED` draft with no executable pending confirmation. The normal confirmation/mutation entry is denied for Voice, regardless of the draft or guest capability.
- Session end clears guest capability and draft through existing session lifecycle.

## Validation

Test capability binding and scope, kernel preparation, canonical slot-reference provenance, session/Redis round-trip, follow-up state continuity, direct confirmation denial with zero mutation calls, and Web V2 compatibility. Re-run the known broader V2 failures separately and classify them without absorbing unrelated fixes into this phase.

## Phase 2B.2 stabilization addendum

The shared V2 vertical booking path must not promote date/daypart/slot phrasing into a provider name. A uniquely resolved `ProviderCandidate` already stored in `BookingDraft` remains the transaction identity for follow-up availability calls; the language adapter should only supply a replacement when the current utterance contains a provider reference. Date normalization continues to use the trusted clinic/tenant `ZoneId`, and the same resolved `LocalDate` must be written to the draft and sent through canonical availability. No mutation boundary or execution policy changes in this stabilization.
