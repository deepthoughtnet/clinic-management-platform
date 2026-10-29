# AIVA V2 Voice Final Confirmation Execution — Phase C

## Scope

Complete the already prepared Voice BOOK flow after a fresh, draft-bound
affirmative confirmation. This phase does not change date parsing, slot
selection, provider resolution, availability retrieval, OTP policy, or any
read-only or non-booking operation.

## Ownership and placement

- `api-bff` owns Voice confirmation handling and invokes
  `VoiceBookingExecutionService` with the current call-bound
  `BookingExecutionContext` and prepared `BookingDraft`.
- `AppointmentBookingApplicationService` remains the shared channel-neutral
  mutation boundary. It validates the patient/tenant context, idempotency, and
  current canonical slot before delegating to `AppointmentService`.
- `appointment-domain` remains the owner of appointment creation and domain
  validation.
- No migration, persistence model, or UI change is expected.

## Execution contract

The prepared draft is authoritative for provider, date, exact time, and
canonical `slotReference`. The final confirmation remains bound to the draft
revision, patient, provider, date/time, slot, availability request/fingerprint,
and current Voice session. The shared application boundary refreshes and
validates canonical availability immediately before mutation. A stale slot
fails without substitution.

Tenant-scoped `CARE_PRIVATE` providers intentionally have no external
`BookingTargetReference`. They use the existing local canonical slot reference
validation in `AppointmentBookingApplicationService`, which recomputes the
tenant/provider/date/time reference, reloads current `AppointmentService`
slots, and then calls `createScheduled`. Providers from other sources continue
to require their canonical booking target reference.

Voice execution retains its stable confirmation idempotency key. No caller
identity is upgraded, no Portal identity is fabricated, and lookup,
cancellation, and rescheduling remain disabled.

## Diagnostics and validation

Safe structured logs identify final-confirmation receipt, prepared draft
presence, shared-boundary invocation, and result without logging caller phone,
OTP, or clinical data. The inbound regression must enter through
`CommunicationTestService`, exercise the real Voice adapter and booking
execution service, and reach the shared application service and appointment
domain mock exactly once. Negative confirmation, replay, and stale-slot
coverage remain required. Local Docker is not restarted by this phase.
