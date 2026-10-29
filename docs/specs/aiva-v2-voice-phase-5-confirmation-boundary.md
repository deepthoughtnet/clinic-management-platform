# AIVA V2 Voice Phase 5 — Confirmation Context and Safe Mutation Boundary

**Status:** Phase 5 COMPLETE. Phase 5C durable-success reconciliation, completed-booking repeat, Docker-enabled regression selector, and architecture gates are verified. The 12 accepted pre-existing assertion failures remain unchanged.
**Owner:** AIVA V2 Voice application orchestration in `api-bff`.
**Persistence/migrations/frontend:** None. The current `SessionProjection.pendingConfirmation` and binding remain authoritative.

## Placement and dependency plan

Phase 5 is limited to a pure confirmation-context predicate, the existing authorized Voice adapter, its deterministic interpretation seam, tests, and this specification. It reuses `BookingExecutionContextFactory`, `VoiceTransactionPolicyResolver`, `VoiceBookingExecutionService`, and the shared `AppointmentBookingApplicationService` boundary. No appointment mutation, authorization, audit/idempotency, persistence, migration, or frontend code is planned to change.

## Current path and ownership

Pending confirmation and draft state live in `SessionProjection`/`BookingDraft`. Slot selection and prepared confirmation creation are performed by the existing `AivaV2TransactionalKernel` preparation path. The authorized inbound Voice flow is orchestrated by `VoiceReadOnlyAivaService`, which builds a call-bound `BookingExecutionContext` and calls `AivaV2VoiceConversationAdapter.prepareAuthorizedVoiceBookingTurn`. The adapter verifies the stored confirmation binding and consumes bounded YES/NO; valid YES calls `VoiceBookingExecutionService.book`. That service rechecks the binding and delegates to `AppointmentBookingApplicationService`, which validates authorization context, idempotency, and the canonical slot before invoking `AppointmentService.createScheduled`.

The kernel `confirm`/`AivaV2BookingTools.confirmBooking` branch is not the authorized Voice mutation route: Voice preparation uses `prepareOnly`, which denies confirmation execution. It remains a compatibility path pending Phase 6 ownership consolidation.

## Phase 5 contract

### Logical context and precedence

`AivaV2VoiceConfirmationContext.isAwaitConfirmation(...)` is the single predicate used by the authorized adapter. It requires an open, unexpired Voice session; an authorization validated `BOOK_APPOINTMENT` execution context bound to the same tenant, patient, provider call, and session; an unexpired prepared/ready draft with canonical tenant provider, date, exact time and selected slot; an unexpired availability snapshot matching draft identity/revision, provider, date and slot; and a live pending confirmation whose fields and existing cryptographic confirmation binding match all of those values. The booking service and application service continue to revalidate authorization and canonical slot at execution.

When that predicate holds, the adapter handles the turn before generic decision gateway fallback. The allowed actions are `CONFIRM`, `DECLINE`, `CHANGE_SLOT`, `CHANGE_DATE`, `CHANGE_PROVIDER` (through Phase 3 identity/switch handling), `SET_TIME_PREFERENCE`, `ABANDON_BOOKING`, `HELP`, `REPEAT`, `END`, and `CLARIFY`. Deterministic context actions take precedence over confirmation polarity. Thus pending Phase 1/3 clarifications retain their higher precedence, and `हाँ` only confirms a booking inside this valid context.

### Answer and state transitions

Positive and negative answers use bounded, anchored phrases, including Hindi, Hinglish and English forms. Uncertain speech such as “शायद” repeats the provider/date/time summary and leaves the draft and binding intact. `REPEAT` likewise replays the current confirmation prompt without changing state. A plain NO clears the pending confirmation and selected slot, returns the same draft to slot collection, and retains provider, date, and availability. Explicitly choosing a different displayed slot, date, daypart, exact time or next date invalidates the old binding before the existing preparation path runs; a newly prepared slot receives a newly computed confirmation binding. Provider changes continue through Phase 3 and cannot be committed by this generic confirmation branch. Explicit abandon clears transient conversation state without booking.

The confirmation question includes canonical provider display name, localized date and time. No patient name is added. Presentation follows the current turn's language/style and existing renderer path.

### Phase 5B confirmation-time transitions

During a valid `AWAIT_CONFIRMATION`, an explicit alternate displayed slot, date, daypart, exact time, or supported time constraint is recognized before YES/NO. The existing binding is cleared and the draft returns to collection before the existing authorized preparation path applies the requested refinement. A new canonical slot selection is prepared through the existing kernel and receives a new binding; an old binding cannot execute after a slot/date/time change. Alternate slot selection revalidates canonically and retains the still-live displayed availability snapshot only when it matches the same draft revision, provider, date, clinic, provider handle, and selected canonical slot. This keeps the displayed choices available without creating another availability owner.

Date changes preserve tenant, patient, and provider while invalidating availability, selected slot, page context, and confirmation. Daypart/time changes preserve provider and date, invalidate the old selection/binding, and return to the established availability/slot preparation path. A daypart refinement may invoke canonical availability for the same provider/date to apply its time constraint; it must not rediscover the provider. A repeat request returns the same provider/date/time confirmation summary and leaves the exact draft, availability snapshot, and confirmation binding untouched; it invokes neither AI nor availability/provider discovery.

The binding invalidation contract is shared through the existing `clearConfirmationForSlotDecision` path: changes to date, selected slot, or time preference clear pending confirmation and selected-slot state before applying the new decision. Provider changes remain under the Phase 3 pending-switch lifecycle. A stale or mismatched confirmation is not treated as a normal change request.

### True stale-slot and generic execution failure evidence

The inbound stale test changes the canonical slot source after the confirmation prompt but before the patient says YES. The real `AppointmentBookingApplicationService` canonical revalidation then returns HTTP 409 through the existing `VoiceBookingExecutionService` boundary. The adapter reports localized `STALE_SLOT`, clears the prior selection and binding, refreshes alternatives through the authorized availability path, and makes no automatic second mutation attempt. The test verifies no durable appointment was created and exactly one final booking attempt occurred.

A separate inbound test makes the existing appointment creation boundary throw a generic runtime failure. It remains `BOOKING_FAILED`, preserves the pending slot/binding and retained availability, and does not call stale recovery or refresh availability. Thus a generic execution error is not classified as a stale slot.

### Mutation, authorization, and reliability

Only bounded `CONFIRM` calls `VoiceBookingExecutionService.book`, passing the existing idempotency key and confirmation binding. The service delegates to the shared `AppointmentBookingApplicationService`; there is no alternate appointment mutation path. The shared application service owns its existing idempotency receipt and canonical availability revalidation. A 409 from the established execution boundary is treated as a true stale slot: the adapter clears the old selection/binding and refreshes alternatives through the existing authorized availability preparation path. Other execution failures are reported as `BOOKING_FAILED`, not mislabeled stale, and do not trigger an automatic retry. A client replay after success finds no active pending confirmation; duplicate persistence remains prevented by the existing idempotency receipt.

Caller phone matching remains identification only. `BookingExecutionContextFactory`, `VoiceTransactionPolicyResolver`, `VoiceBookingExecutionService`, `AppointmentBookingApplicationService`, and `AppointmentService` semantics are unchanged. AI is not invoked while a valid pending confirmation is handled; it cannot authorize, confirm, mutate, or retry.

### Phase 5C durable success, reconciliation, and completed repeat

The shared `AppointmentBookingApplicationService` transaction and its existing idempotency receipt are the durability authority. A receipt stores the serialized `AppointmentRecord` (including appointment id/reference, tenant, patient, provider, date/time, and status) under the existing scoped execution key and request fingerprint. `AppointmentBookingApplicationService.findExistingBooking(...)` is a read-only, trusted-context-scoped lookup of that same receipt; it is not a new booking ledger and does not alter `book(...)` or receipt semantics. `VoiceBookingExecutionService.reconcile(...)` validates the current confirmation binding before invoking that read-only lookup.

On a normal success, Voice constructs a lightweight `CompletedBookingOutcome` only after the application boundary returns the durable appointment result, then atomically finalizes the Voice session and response. The outcome retains appointment id/reference, provider display identity, date/time, completion timestamp, tenant/patient/call correlation, the existing idempotency reference, source `VOICE`, and status `CONFIRMED`; it does not copy the appointment record or add new PHI. Deterministic repeat/status requests on the same trusted session return a localized summary without mutation, provider discovery, availability, or AI. A repeated YES after completion cannot reach the pending-confirmation execution path. An explicit “one more appointment” request starts a separate draft/execution identity while retaining the prior completed outcome as a historical result.

Before consuming a still-valid confirmation, the adapter first checks the existing receipt for the same bound execution. If a prior attempt committed but session finalization failed, the receipt is converted into `CompletedBookingOutcome`; the flow returns success without another mutation. The focused failure-injection test commits through the shared application service, then fails the session store write before the completed state is persisted. Replaying the same accepted YES/repeat reconciles the receipt and leaves exactly one appointment. No mutation retry is performed during reconciliation.

If receipt lookup is unavailable/unknown, Voice does not guess success and does not retry. It returns a localized “checking booking status” response and preserves the pending context for a later replay. Session reads now distinguish `SESSION_NOT_FOUND` (empty result), `SESSION_READ_FAILED/UNAVAILABLE` (exception), and `SESSION_CORRUPT` (exception); unavailable/corrupt data is not deleted or silently treated as a fresh session. The store retains its current session lifetime/ownership and no durable ledger was added.

Correlation limitation: recovery requires the same trusted session/call and execution/idempotency identity. If the session has expired/been explicitly closed, or a later call has a new correlation identity, the current architecture has no safe way to associate a patient’s free-form status question with the prior execution receipt. Voice does not search receipts by patient-supplied booking reference/provider name. Unknown synchronous outcomes are reported as pending status verification; no background task or blind retry is created.

### Phase 5C test evidence

- DotVoice inbound tests cover normal success followed by repeat/status question/duplicate YES, injected post-commit session finalization failure followed by same-session receipt reconciliation, and explicit additional-booking separation. Each asserts exactly one appointment and no extra provider/availability/AI/mutation calls on deterministic follow-up.
- Session projection serialization round-trips the completed outcome. Session-store tests distinguish missing, unavailable, and corrupt state and verify failed/corrupt reads do not delete the session.
- Voice execution tests verify the read-only reconciliation route uses the existing scoped receipt and does not invoke a second mutation.
- Crash-equivalent behavior is tested at the application commit/session finalization boundary. A literal process kill is not required to establish the same durable ordering invariant.

### Ownership and compatibility

`SessionProjection.pendingConfirmation` and `latestAvailabilityResult` remain the only owners of the binding and availability snapshot. The adapter's bounded confirmation-context branch is authoritative for authorized Voice. Kernel `confirm` and `AivaV2BookingTools.confirmBooking` remain **COMPATIBILITY — PHASE 6 REMOVAL**; authorized Voice's `prepareOnly` capability gate prevents this path from mutating appointments. The application's booking execution and idempotency code remain authoritative for mutation and duplicate/retry safety.

## Validation record

### Final validation

- DotVoice inbound path after Phase 5B: 47/47 passed, including change-slot, change-date, change-daypart, repeat, true canonical stale-slot recovery, and generic execution-error distinction. Existing Hindi/Hinglish success, duplicate-turn replay, ambiguity preservation, and decline cases remain covered.
- Voice adapter: 74/74 passed.
- Language adapter: 32/32 passed.
- Voice booking execution: 5/5 passed.
- Authorized booking: 5/5 passed in the focused run.
- Architecture selectors: 7/7 passed.
- Testcontainers retry: availability boundary 1/1; transactional reliability 9/9.
- Phase 5B targeted DotVoice command: `mvn -pl backend/api/api-bff -am test '-Dtest=AivaV2VoiceDotVoiceInboundPathTest' -Dsurefire.failIfNoSpecifiedTests=false` — 47/47 passed.
- Focused cross-phase run: adapter 74/74, language 32/32, kernel 42/42, Voice execution 5/5, authorized guest-preparation bridge 2/2, DotVoice 47/47, and architecture tests 7/7 passed. Gateway was 78/79; its one failure is the accepted Phase 4 assertion `activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty`.
- Fresh authoritative Phase 0 selector command (same selector as the baseline spec): 563 tests, 551 passed, 12 assertion failures, 0 errors, 0 skipped. These are the same 12 accepted Phase 4 failures listed in `aiva-v2-voice-phase-4-collection-context-boundary.md`; no new persistent failure appeared. The changed test count from prior Phase 5 broad runs reflects the current selector's collected tests; failure identity comparison is unchanged.
- Testcontainers ran in the authoritative selector without rebuild/restart: availability boundary 1/1 and transactional reliability 9/9 passed. No infrastructure errors occurred.
- `git diff --check` passed after the implementation and spec updates.
- The same 12 Phase 4 assertion-failure identities remain; no new persistent failure was introduced. Their identity mapping remains in `aiva-v2-voice-phase-4-collection-context-boundary.md`.
- `git diff --check` passes.

### Phase 5C validation (final run)

- Focused Phase 5C selector: DotVoice inbound 51/51, session serialization 3/3, session read-failure 3/3, Voice booking execution 5/5 passed. Distributed-session Testcontainers class was skipped by its configured opt-in profile in this focused run.
- Docker-enabled authoritative selector command (same selection as the Phase 0 baseline): `mvn -pl backend/api/api-bff -am test '-Dtest=AivaLanguageAdapterTest,AivaResponseRendererTest,AivaSpokenDateFormatterTest,AivaTransactionCapabilityGateTest,AiAivaV2ConversationDecisionGatewayTest,AivaV2*Test,VoiceBookingExecutionServiceTest,VoiceReadOnlyAivaService*Test,VoiceTransactionPolicyResolverTest,CommunicationTestServiceTest,CommunicationTestWiringContextTest,CommunicationTestControllerTest,DotVoiceInboundListenerLifecycleTest,PatientPortalServiceTest,AppointmentServiceTest' -Dsurefire.failIfNoSpecifiedTests=false` — 571 tests executed, 559 passed, 12 assertion failures, 0 errors, 0 skipped. The extra eight executed tests are the Testcontainers tests that are skipped in the no-Docker baseline run.
- All 12 broad assertion failures exactly match the accepted Phase 4 identity list; there are no new persistent failures. The Testcontainers availability boundary passed 1/1, transactional reliability passed 9/9, and Redis distributed-session storage passed 12/12.
- Focused Phase 5C command: DotVoice inbound 51/51, session serialization 3/3, session read-failure 3/3, and Voice booking execution 5/5 passed. The separate architecture command passed 7/7 (CommercialPlatform 4/4; CommercialCatalog 3/3).
- The initial no-Docker broad attempt produced 2 infrastructure errors only; the approved Docker-access rerun completed all integration classes without errors. No Docker build or restart was run.
- `git diff --check` passed after the final test/spec changes.

### Remaining Phase 5 gaps

- Cross-session/new-call correlation after expiry or explicit session closure remains intentionally unsupported without a trusted correlation mechanism. Unknown synchronous outcomes remain pending status verification; the Voice path does not fabricate success, retry mutation, or start background work.
- Phase 6 compatibility cleanup is not part of this phase.

Phase 6 cleanup targets remain the duplicate compatibility confirmation path in gateway/kernel and broader adapter/gateway decision consolidation.
