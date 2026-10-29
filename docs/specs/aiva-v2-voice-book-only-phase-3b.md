# AIVA V2 Voice Book-Only Enablement — Phase 3B

## Scope and safety

Enable only Voice `BOOK_APPOINTMENT`, and only when the resolved clinic/tenant
policy, call-bound patient trust/capability, prepared booking draft, canonical
slot reference, and a fresh explicit booking confirmation all validate. Voice
lookup, cancellation, rescheduling, and clinical data remain disabled. The
application default for `booking-enabled` is false. No production or global
tenant policy is enabled by this feature.

The Voice adapter must invoke the Phase 3A `AppointmentBookingApplicationService`
with an explicit `BookingExecutionContext`; it must not call
`PatientPortalService.bookAppointment`, create Portal security/request context,
or use the V2 Web confirmation mutation tool. Web behavior remains unchanged.
Voice booking preparation resolves provider candidates only within the trusted
tenant supplied by DID routing. It must not call Portal-only
`careAiDoctorsAcrossAuthorizedClinics()` or depend on a Patient Portal request
context; Web preparation retains its existing authorized-clinic provider path.

## Placement

- `api-bff` owns Voice policy resolution, call-scoped confirmation/session
  orchestration, and invocation of the existing shared booking application
  boundary.
- `appointment-domain` remains authoritative for appointment creation and
  domain validation; existing canonical availability, stale-slot validation,
  and idempotency remain in force.
- Existing V173/V174 migration files support OTP binding and truthful audit
  actor/channel data. Phase 3B introduces no schema migration.
- No frontend/admin changes. Enablement is supplied through existing
  clinic/tenant policy overrides; defaults remain fail-closed.

## Trust and confirmation invariants

- Supported paths are valid call-bound OTP authorization, explicit-policy
  preverified phone trust, and a valid call-bound guest-booking capability.
- A registered-unverified match is not authentication and does not receive
  patient-owned capabilities. It must not cause duplicate patient creation.
- Guest and preverified actors are represented truthfully; no Portal user ID is
  fabricated. Existing guest capabilities remain booking-only.
- Final affirmative confirmation is tied to the active draft revision,
  patient, provider, date/time, canonical slot reference, availability request
  and fingerprint, and the current session. Any changed field invalidates it.
- The shared booking application service revalidates the canonical slot before
  calling the appointment domain. A stale slot is never silently substituted.
- Repeated execution uses a stable, non-transcript idempotency key derived from
  call/session and draft/confirmation identity.
- Only `BOOK_APPOINTMENT` may execute. Lookup, cancellation, and rescheduling
  remain blocked even when booking policy is enabled.

## Configuration

The preferred operator configuration is:

```yaml
voice:
  booking-enabled: false
  otp-required: true
  guest-booking-enabled: false
```

The application defaults are fail-closed for booking and guest registration;
OTP is required by default. Effective policy continues to resolve clinic →
tenant → application. The legacy `clinic.aiva.voice.transaction-auth` policy
fields remain accepted, but preferred `voice.*` application settings take
precedence over legacy application defaults. Explicit clinic/tenant overrides
take precedence over both.

`otp-required: true` starts the existing exact-challenge OTP flow for registered
patient booking. `otp-required: false` suppresses OTP and permits only the
restricted current-new-booking path for a tenant-scoped single patient match;
it does not authenticate the patient or grant personal-data capabilities.
No-match callers remain governed separately by `guest-booking-enabled`.
`booking-enabled: false` remains the booking kill switch. Lookup, cancellation,
and rescheduling remain disabled regardless of these settings. No auth bypass
is supported.

## Validation

Cover policy precedence/default, all trust paths, explicit final confirmation
and invalidation, shared application-service invocation, canonical slot
revalidation, idempotency, guest/unverified restrictions, transaction
operation isolation, Web booking compatibility, Spring wiring, migrations
V173/V174 in integration tests, Maven package, and `git diff --check`.
