# AIVA Real Service Contract Certification

## Status

Approved by the September 2026 real-service certification task. This phase certifies and normalizes existing contracts only; it does not implement AIVA V2.

## Scope

- Exercise patient portal, appointment, Provider/Discover, and AIVA skill adapters with local UAT identities and database-backed services.
- Preserve appointment-domain rules, RBAC, Care entitlement, Discover publication, CALL_TO_BOOK, and voice transport.
- Fix only reproduced P0/P1 contract defects and protect the fixes with focused regression tests.

## Placement

- `api-bff` owns patient-facing DTO normalization and AIVA application adapters.
- `appointment-domain` remains the owner of schedule, capacity, appointment status, and transaction invariants.
- No persistence schema, migration, frontend, or voice change is required.

## Certified Contract Changes

1. AIVA appointment lookup delegates to `PatientPortalService.careAiUpcomingAppointments()`, the existing patient-owned, date-aware service path. Debug projections are not application contracts.
2. A deterministic extracted ISO date outranks a conflicting model-provided date. The model remains a fallback when deterministic extraction has no date.
3. Location extraction may emit registry-backed location aliases only. A generic proper noun is an unvalidated signal and cannot become a doctor-search filter.
4. Private Care availability returns a stable local slot reference. Booking validates that reference against the same tenant, doctor, date, time, and current authoritative schedule. Existing clients may continue omitting it.

## Out Of Scope

- AIVA V2, new fallback routing, new phrase rules, or workflow redesign.
- Provider/Discover publication or entitlement changes.
- Concurrent idempotency-key response normalization unless a duplicate mutation is proven.
- A new global API error taxonomy.

## Acceptance

- Real Care doctor search, availability, booking, lookup, cancellation, reschedule, CALL_TO_BOOK, and cross-patient isolation are exercised in the local UAT tenant.
- Focused adapter and patient portal tests pass.
- Relevant backend tests and package build pass.
- `git diff --check` is clean.
