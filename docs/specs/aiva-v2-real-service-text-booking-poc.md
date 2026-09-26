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

### Active Booking Follow-up and Displayed Slot Selection

An active booking draft supplies bounded semantic context (`activeWorkflow`,
`pendingField`, provider display name, specialty, date, time window, and exact
time). When the pending field is provider or specialty, a short doctor phrase is
treated as a provider candidate and is still validated by the authoritative
provider resolver. No alias or fuzzy auto-selection is introduced.

When a current availability page is displayed, ordinal/index controls and exact
times are resolved deterministically against that displayed page. Generic forms
such as `3`, `third`, `3rd`, and action-wrapped equivalents select only the
corresponding displayed slot. A uniquely matching `18`, `18:00`, or `6 PM` may
select the displayed 18:00 slot. Invalid indices and times never select a hidden
or invented slot.

Unrecognized or invalid turns preserve the active draft and current availability.
Slot selection remains subject to the existing draft id, revision, availability
request, criteria fingerprint, provider, clinic, date, and expiry checks. Stale
selection is rejected or refreshed through the existing kernel path.

V2 also supports read-only upcoming appointment lookup through a dedicated
`PatientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()` path.
That path aggregates only the authenticated patient's existing Care-authorized
clinic scopes. The legacy single-clinic `careAiUpcomingAppointments()` path is
unchanged. Lookup filters are applied only to the authorized service result and
do not create or mutate a booking draft, availability result, or pending
confirmation.

## Compatibility And Rollback

- Endpoint: `/api/patient-portal/aiva-v2/message`.
- Feature flag: `aiva.v2.enabled`, default `true`.
- The endpoint is enabled by default for the patient-facing Care experience.
- Set `AIVA_V2_ENABLED=false` and `VITE_AIVA_V2_ENABLED=false` only as an
  internal emergency rollback; these flags are not exposed in patient UI.
- Legacy `PatientPortalCareAiService`, CanonicalTurn, reducer shadow runtime,
  voice transport, and existing endpoints are unchanged.
- Rollback is explicitly disabling the engine flags; no data migration or
  backfill is required.

## Safety Invariants

- Suggested/near provider names never mutate authoritative provider identity.
- Semantic interpretation receives only bounded booking context: active workflow,
  derived pending field, safe provider display name, specialty/date/time criteria,
  and small state flags. It never receives provider IDs or raw provider objects.
- When a booking draft is waiting for provider or specialty, the semantic layer
  interprets the next provider/specialty phrase as a booking update; the kernel
  still performs authoritative catalog resolution.
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
- Regressions for pending-provider follow-up, date preservation, displayed-page
  ordinal and exact-time selection, invalid index rejection, and state retention
  after an unrecognized turn.
- Patient portal, appointment, availability, and CALL_TO_BOOK regressions.
- API BFF architecture tests and backend package build.
- `git diff --check` clean.
