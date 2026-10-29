# AIVA V2 Voice Guest Registration — Phase 2B

## Scope

Permit only tenant-routed Voice `BOOK_APPOINTMENT` requests with caller identification `NO_MATCH` to enter a policy-gated, consent-first minimal patient registration flow. A successful registration may receive a short-lived, call-bound `GUEST_BOOK_APPOINTMENT` grant for preparation only. Final appointment mutation, patient-owned appointment lookup, cancellation, and rescheduling remain disabled.

## Placement and dependencies

- Patient identity creation and duplicate-mobile invariant remain owned by `patient-domain` (`PatientService` and its existing `PatientUpsertCommand`); Voice does not access repositories or entities.
- Guest-flow orchestration, policy resolution, Voice state, and the narrow authorization contract remain in `api-bff` alongside existing AIVA V2 Voice code.
- Reuse the existing `SessionProjection`, Voice session key/lifecycle, `VoiceTransactionPolicyResolver`, `PatientService.findActiveByTenantAndMobile`, `PatientService.create`, and existing transaction capability gate.
- No parallel patient store, session store, duplicate engine, or slot engine is introduced.
- No schema migration or frontend/admin UI is included. Existing patient creation audit is extended with truthful VOICE source/correlation metadata; no portal app-user or login is created.

## Security invariants

- Only trusted tenant routing plus `NO_MATCH`, `BOOK_APPOINTMENT`, and enabled guest policy can enter the flow. All other identification statuses and transaction intents fail closed.
- Consent and explicit caller-mobile confirmation precede patient creation. Only name, confirmed number, and `PatientGender.UNKNOWN` are supplied; the patient domain accepts DOB/age as optional.
- Duplicate lookup is tenant-scoped and repeated immediately before canonical create. A detected match never creates another patient; lookup errors and create conflicts fail closed.
- A created guest is registered but not authenticated. The grant is bound to tenant, patient, VOICE channel, call/session, interaction, and expiry, and grants only `GUEST_BOOK_APPOINTMENT` preparation.
- The guest grant cannot authorize patient-owned lookup, cancellation, rescheduling, or final booking confirmation. Call/session close clears it.
- Existing Patient Portal registration/login/token semantics are unchanged. No invitation/account-linking capability currently exists; this phase does not fabricate one.

## Booking preparation boundary

Voice continues to use the current read-only provider/schedule/slot tools. The existing V2 `SessionProjection` is retained as the sole workflow state. The current Voice adapter does not invoke the V2 transactional kernel; this phase must not weaken that boundary to obtain a draft. Canonical slot-reference/draft preparation remains deferred unless it can be called through a preparation-only kernel entry that cannot reach mutation.

## Validation

Cover policy defaults/precedence, identification eligibility, consent and mobile confirmation, duplicate outcomes and race conflicts, domain creation/audit source, narrow guest grant scope/binding/expiry, session cleanup, no portal token or account creation, and hard-disabled transaction execution. Preserve Phase 2A registered authentication and Web Portal behavior.
