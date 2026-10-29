# AIVA V2 Voice Booking Mutation Boundary — Phase 3A

## Scope

Extract the existing Patient Portal booking mutation orchestration behind a
channel-neutral, explicit booking execution context. Preserve Web authentication,
patient ownership, canonical slot revalidation, idempotency, and appointment-domain
validation/events. Add truthful actor metadata for Portal, authenticated Voice, and
Voice guest booking. Voice only constructs and validates context in this phase;
Voice mutation execution remains disabled.

## Placement and dependencies

- `api-bff` owns the booking application orchestration boundary and its Web/Voice
  adapters. It may call patient, availability, clinic, identity, and appointment
  domain services; it must not add repository declarations or persistence.
- `appointment-domain` remains authoritative for slot capacity, duplicate booking,
  schedule, tenant, and appointment lifecycle validation. It propagates actor
  metadata to audit and the existing appointment-booked event.
- `platform-audit` owns structured actor/channel/correlation audit fields and
  backward-compatible audit command/record contracts.
- Migration execution remains in `api-bff` resources. Repository-wide migration
  scan identifies V173 as the highest current version; this feature uses the next
  available version only if structured audit fields require schema support.
- No frontend changes.

## Invariants

- Web `@PreAuthorize(PATIENT)`, `RequestContextHolder`-derived identity, current
  patient resolution, and existing self/tenant ownership remain unchanged.
- Voice contexts are built only from verified registered-patient grants or valid,
  call-bound guest-registration capability; patient ID alone never authorizes.
- Guest actors have no fabricated `appUserId`; actor UUID is nullable and actor
  type/channel/call interaction are explicit.
- Canonical selected `slotReference` is reloaded and checked immediately before
  `AppointmentService.createScheduled`; no Voice-side mutation or slot engine.
- Idempotency key semantics and booking events remain compatible.
- Voice booking execution is hard-disabled in Phase 3A; lookup, cancellation, and
  rescheduling remain disabled.

## Validation

Test Web compatibility, context binding and ownership, canonical slot revalidation,
idempotency replay, actor metadata, Voice context construction, hard-disabled Voice
execution, appointment-domain tests, Spring wiring, architecture tests, Maven package,
and `git diff --check`.
