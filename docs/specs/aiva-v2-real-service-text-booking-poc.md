# AIVA V2 Real-Service Text Booking POC

## Status

Approved for an isolated Phase 1 proof of concept. The endpoint is disabled by
default and does not replace or share mutable state with legacy CareAI.

## Ownership And Placement

- Owning context: patient portal booking application orchestration.
- API module: `backend/api/api-bff`.
- Business owners: appointment-domain for schedule and booking truth; patient
  and Discover services for authorized/private and published provider lookup.
- Persistence: none. POC draft, latest-result, and confirmation projections are
  bounded, expiring, in-memory session state.
- Migration owner: none.
- Frontend and voice: out of scope.

## Runtime Boundary

`Text -> provider-neutral decision -> transactional kernel -> V2 tool adapter
-> existing Jeevanam service -> structured result -> revisioned draft ->
deterministic response`

The semantic gateway may propose text candidates and operations. It cannot
provide patient, tenant, provider, clinic, slot, authorization, or transaction
truth. The kernel validates ordering and current revision. Existing services
remain authoritative for identity visibility, availability, slot validity,
CALL_TO_BOOK, authorization, and booking.

## Scope

The first vertical slice supports provider resolution, availability, slot
selection, booking preparation, confirmation, and draft abandonment. It uses
one active provider-result and one active availability-result per V2 session.
Criteria changes revoke confirmation and invalidate slot/result references.

V2 also supports read-only upcoming appointment lookup through a dedicated
`PatientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()` path.
That path aggregates only the authenticated patient's existing Care-authorized
clinic scopes. The legacy single-clinic `careAiUpcomingAppointments()` path is
unchanged. Lookup filters are applied only to the authorized service result and
do not create or mutate a booking draft, availability result, or pending
confirmation.

## Compatibility And Rollback

- Endpoint: `/api/patient-portal/aiva-v2/message`.
- Feature flag: `aiva.v2.enabled`, default `false`.
- The endpoint is absent unless explicitly enabled.
- Legacy `PatientPortalCareAiService`, CanonicalTurn, reducer shadow runtime,
  voice transport, and existing endpoints are unchanged.
- Rollback is disabling the flag; no data migration or backfill is required.

## Safety Invariants

- Suggested/near provider names never mutate authoritative provider identity.
- A slot can be selected only from the latest result matching draft revision.
- Preparing a booking revalidates availability and capability.
- Positive confirmation uses only the current server-held confirmation
  reference and never repeats provider or availability lookup.
- Each confirmation has one stable idempotency key.
- Duplicate confirmation cannot produce a second appointment.
- Provider fallback must conform to the same decision schema.
- No raw patient identifiers, credentials, or arbitrary tool payloads are
  emitted in V2 traces.

## Validation

- Focused semantic-gateway, kernel, store, tool-adapter, and controller tests.
- Patient portal, appointment, availability, and CALL_TO_BOOK regressions.
- API BFF architecture tests and backend package build.
- `git diff --check` clean.
