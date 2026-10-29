# AIVA V2 Voice — Provider Continuity Fix 1

## Scope

Restore continuity between the existing call-scoped `activeVoiceDoctor` and
the V2 booking-preparation draft. This change is limited to provider identity,
provider-suggestion confirmation, and rejecting non-name text as a doctor
reference. It does not change booking mutation, slot selection, date handling,
OTP, lookup, cancellation, or rescheduling.

## Ownership and placement

- `api-bff` owns Voice turn normalization, session orchestration, and the
  channel-neutral booking preparation bridge.
- `AivaV2Models.SessionProjection.activeVoiceDoctor` remains the canonical
  Voice provider context; booking draft provider identity must adopt it when
  the caller has not explicitly requested another provider.
- `AivaV2BookingTools` remains the canonical tenant-scoped booking provider
  resolver. It may use the existing `DoctorNameNormalizer` for deterministic
  name equivalence but must not add fuzzy aliases or a second resolver.
- The V2 transactional kernel consumes its existing session provider result
  when the caller confirms a bounded provider suggestion.
- No frontend, domain persistence, migration, or new session store is needed.

## Invariants

1. A unique canonical provider found in the tenant resolves directly for
   booking preparation.
2. A provider suggestion already stored in the V2 session is the target of a
   subsequent affirmative confirmation; the confirmation is not treated as a
   fresh provider query.
3. A current explicit different provider overrides the active provider.
4. Date, appointment, action, availability, and time words cannot form a
   provider reference.
5. Read-only discovery/schedule/availability continue to use their existing
   deterministic Voice paths.
6. No appointment mutation is added or enabled by this change.

## Validation

Add sequential V2 session tests for direct canonical resolution, suggestion
confirmation, active-provider continuation, explicit provider override, and
false-name rejection. Retain existing read-only provider discovery, schedule,
slots, and contextual-provider tests.
