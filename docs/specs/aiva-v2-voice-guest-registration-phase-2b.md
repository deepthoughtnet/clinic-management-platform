# AIVA V2 Voice Quick Registration — True New Callers

## Scope

Permit only tenant-routed Voice `BOOK_APPOINTMENT` requests with caller identification `NO_MATCH` and `GUEST_CALLER` party state to enter a policy-gated, consent-first minimal patient registration flow. Successful registration creates/reuses a patient through the patient domain and upgrades only the call-bound restricted new-booking identity. Final confirmation remains mandatory; protected patient-owned operations, cancellation, and rescheduling remain denied.

## Placement and dependencies

- Patient identity creation and duplicate recheck remain owned by `patient-domain` (`PatientService` and its existing `PatientUpsertCommand`); Voice does not access repositories or entities. A shared contact number is not treated as proof of identity or a one-person invariant.
- Guest-flow orchestration, policy resolution, Voice state, and the narrow authorization contract remain in `api-bff` alongside existing AIVA V2 Voice code.
- Reuse the existing `SessionProjection`, Voice session key/lifecycle, `VoiceTransactionPolicyResolver`, `PatientService.findActiveByTenantAndMobile`, `PatientService.createVoiceQuickRegistration`, and registered-unverified restricted booking gate.
- No parallel patient store, session store, duplicate engine, or slot engine is introduced.
- No schema migration or frontend/admin UI is included. Existing patient creation audit records truthful Voice source, consent, and correlation metadata; no portal app-user or login is created.

## Security invariants

- Only trusted tenant routing plus `NO_MATCH`, `GUEST_CALLER`, `BOOK_APPOINTMENT`, and enabled `voiceQuickRegistrationEnabled` tenant policy can enter the flow. All other identification statuses and transaction intents fail closed.
- Consent precedes collection of preferred first name and approximate completed age. The normalized inbound caller mobile is used as a contact/lookup key without asking the caller to repeat it or treating it as identity proof. Surname is blank, gender is `UNKNOWN`, and DOB remains null.
- Voice age capture accepts a whole-year numeric value or a recognized age phrase, normalizes harmless surrounding punctuation, and enforces the existing 0–120 bounds. Decimal and out-of-range values remain in age clarification; duplicate recheck starts only after valid age capture.
- Duplicate lookup is tenant-scoped and repeated immediately before canonical create. Zero matches creates one minimal patient, one match safely reuses that patient, and multiple matches or lookup/create uncertainty fail closed without creating another record.
- A created/reused caller is registered but not authenticated, phone verified, OTP verified, or Care logged in. `REGISTERED_UNVERIFIED` identity is bound to tenant, patient, VOICE channel, call/session and interaction. `personalDataAllowed` remains false; only restricted new booking is allowed.
- Quick registration cannot authorize patient-owned lookup, cancellation, rescheduling, or protected data operations. Final confirmation and canonical stale-slot validation remain required. Call/session close clears the capability.
- Existing Patient Portal registration/login/token semantics are unchanged. No invitation/account-linking capability currently exists; this phase does not fabricate one.

## Booking boundary

The existing V2 `SessionProjection` is retained as the sole workflow state and preserves non-sensitive provider, specialty, date, daypart, and displayed-slot context over registration. No booking mutation occurs before registration finishes. The normal booking execution and final-confirmation route remains authoritative, revalidates any displayed slot, and retains its idempotency behavior.

## Validation

Cover policy defaults/precedence, identification eligibility, explicit consent, name and age validation, duplicate outcomes and concurrent creates, domain creation/audit source, restricted booking scope/binding/expiry, session cleanup, no portal token or account creation, mandatory final confirmation, and protected-operation denial. Preserve Phase 2A registered authentication and Web Portal behavior.
