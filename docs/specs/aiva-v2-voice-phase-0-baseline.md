# AIVA V2 Voice Phase 0 — Baseline and Protection Contract

**Status:** Phase 0 evidence snapshot; no production behavior changed.
**Baseline date:** 2026-10-25.
**Scope:** Current working tree, including existing uncommitted changes already present before this Phase 0 task.
**Runtime:** Source/test inspection only; no Docker image rebuild or runtime restart.

This document records the current test and ownership baseline for the staged simplification. It is not a second architecture proposal. The target direction and migration approach remain in [AIVA Controlled Runtime Simplification](aiva-controlled-runtime-simplification.md); the slot-page rule remains in [slot-page continuation normalization](aiva-v2-voice-slot-page-continuation-normalization.md).

## 1. Fresh test baseline

The authoritative focused command was:

```bash
mvn -pl backend/api/api-bff -am test \
  '-Dtest=AivaLanguageAdapterTest,AivaResponseRendererTest,AivaSpokenDateFormatterTest,AivaTransactionCapabilityGateTest,AiAivaV2ConversationDecisionGatewayTest,AivaV2*Test,VoiceBookingExecutionServiceTest,VoiceReadOnlyAivaService*Test,VoiceTransactionPolicyResolverTest,CommunicationTestServiceTest,CommunicationTestWiringContextTest,CommunicationTestControllerTest,DotVoiceInboundListenerLifecycleTest,PatientPortalServiceTest,AppointmentServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Full output: `/tmp/aiva-v2-phase0-authoritative-baseline.log`.

The run initially reported **519 tests: 487 passed, 13 failed, 2 errors, 17 skipped**. The two errors were Testcontainers startup errors because the sandbox denied access to `/var/run/docker.sock`; the exception was “Could not find a valid Docker environment.” The affected classes were rerun with local Docker socket access, without rebuilding images, using:

```bash
mvn -pl backend/api/api-bff -am test \
  '-Dtest=AivaV2AvailabilityServiceBoundaryIntegrationTest,AivaV2RealTransactionalReliabilityIntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- `AivaV2AvailabilityServiceBoundaryIntegrationTest`: 1 passed.
- `AivaV2RealTransactionalReliabilityIntegrationTest`: 9 passed. Its initial class-level setup error had prevented the first run from executing its test methods.

After replacing the two infrastructure errors with the retry results, the effective baseline is **527 tests: 497 passed, 13 failed, 0 errors, 17 skipped**. The eight-test increase is from the previously unexecuted methods in `AivaV2RealTransactionalReliabilityIntegrationTest`. Retry reports are in `backend/api/api-bff/target/surefire-reports`; the original complete Maven output remains in the temporary log above. Docker image build/rebuild was not run.

An initial broader selector, logged at `/tmp/aiva-v2-phase0-baseline.log`, also selected generic `VoiceOrchestratorServiceTest` and legacy `VoiceTestWebSocketHandlerTest`; those tests are not part of the AIVA V2 inbound baseline. They failed because their mocks expect voice-turn metadata from an alternate orchestrator route. Their results are intentionally excluded from the 519-test authoritative AIVA baseline; they remain evidence that those tests cover a different contract.

## 2. Failure inventory

`Production path` below is the code exercised by the test, not a claim that the test reproduces DotVoice transport. “Impact” distinguishes direct effect on the current BOOK path from a separate test domain.

| Test class and method | Expected → actual; first failure | Production path and area | Primary category | BOOK impact |
|---|---|---|---|---|
| `AiAivaV2ConversationDecisionGatewayTest.activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty` | Expected `AiOrchestrationService.complete(...)`; mock had zero interactions. | `AiAivaV2ConversationDecisionGateway.decide` → deterministic `fastPath`; gateway booking/provider-specialty interpretation. | **B. STALE_TEST_EXPECTATION** — current deterministic route short-circuits the model call the test assumes. | INDIRECT |
| `AiAivaV2ConversationDecisionGatewayTest.answerEnvelopeIsAccepted` | Expected doctor patch `Dr. Akshu Kumar`; actual `Akshu Kumar`. | Gateway answer-envelope parse and canonical fact merge; provider name formatting. | **B. STALE_TEST_EXPECTATION** — the current canonicalized name drops the honorific while the assertion still requires it. | NO (presentation/evidence only) |
| `AivaV2ActiveBookingFollowUpRegressionTest.pendingProviderFieldTreatsShortDoctorPhrasesAsAuthoritativeResolverCandidates` | For `Talk Akshu Kumar`, expected `Akshu Kumar`; actual `Talk Akshu Kumar`. | Language adapter → gateway deterministic pending-provider extraction; provider phrase cleanup. | **H. PROVIDER_DISCOVERY** — filler prefix is retained as provider evidence. | YES |
| `AivaV2ActiveBookingFollowUpRegressionTest.providerResolutionAdvancesWorkflowWithoutLosingRequestedDate` | Expected canonical provider candidate; actual `null`. | Gateway decision → `AivaV2TransactionalKernel` → canonical provider adoption; test uses `tenant` rather than a UUID tenant accepted by canonical adoption. | **C. TEST_FIXTURE_DRIFT** — fixture tenant representation does not meet the current tenant-canonicalization boundary. | YES (test cannot reach the intended assertion) |
| `AivaV2LanguageBoundaryVerticalTest.fullBookingConversationUsesRealAdapterGatewayKernelToolsAndRendererInAllStyles` | Expected `NEED_DATE`; actual `PROVIDER_NOT_FOUND`. | `AivaV2ConversationService.message` → gateway/kernel/tools; short `Dr Akshu` against fixture provider `Doc Akshu Kumar`. | **H. PROVIDER_DISCOVERY** — fixture/provider naming does not resolve the expected identity. | YES |
| `AivaV2LanguageBoundaryVerticalTest.serviceSessionKeysIsolatePatientConversationTenantAndPresentation` | Expected `NEED_DATE`; actual `PROVIDER_NOT_FOUND`. | Service → provider resolution before the test can verify tenant/patient/presentation isolation. | **H. PROVIDER_DISCOVERY** — same short-name/fixture resolution issue blocks the isolation assertion. | INDIRECT (intended assertion is session isolation) |
| `AivaV2LanguageBoundaryVerticalTest.rescheduleConversationUsesRealServiceToolsAndStructuredResponsesInAllStyles` | Expected `RESCHEDULE_AVAILABLE_SLOTS`; actual `RESCHEDULE_NO_MORE_SLOTS`. | Conversation service → reschedule tool/result rendering; reschedule pagination. | **F. RESCHEDULE_ONLY** | NO |
| `AivaV2LanguageBoundaryVerticalTest.standaloneControlTurnsKeepEstablishedConversationPresentationThroughService` | Expected `AVAILABLE_SLOTS`; actual `NEED_PROVIDER`. | Conversation service → provider resolution/draft continuation; the test starts with `Dr Akshu` while the fixture catalog entry is `Doc Akshu Kumar`. | **H. PROVIDER_DISCOVERY** — provider does not bind before the date/slot continuation assertion. | YES |
| `AivaV2PocCertificationTest.callToBookNeverInvokesAvailabilityOrBooking` | Expected `CALL_TO_BOOK`; actual `PROVIDER_NOT_FOUND`. | Gateway/kernel/tools; provider fixture cannot be canonically resolved, so the call-to-book branch is not reached. | **C. TEST_FIXTURE_DRIFT** — the test’s injected provider setup does not satisfy current canonical provider adoption. | YES (branch remains untested) |
| `AivaV2PocCertificationTest.englishHappyPathMoreSlotsContextQuestionAndDuplicateConfirmation` | Expected `SLOT_CHOICES`; actual `PROVIDER_NOT_FOUND`. | Service → gateway/kernel/tools; provider name `Akshu` does not resolve in the configured fixture. | **C. TEST_FIXTURE_DRIFT** — test does not provide a provider fixture compatible with the current canonical resolver. | YES |
| `AivaV2PocCertificationTest.hindiAndEnglishConfirmationHaveIdenticalKernelSemantics` | Expected `CONFIRMATION_REQUIRED`; actual `CLARIFICATION` for Hindi affirmative confirmation. | Conversation service → language/control normalization → gateway/kernel confirmation handling. | **I. LANGUAGE_PRESENTATION** — Hindi confirmation does not follow the expected confirmation route. | YES |
| `AivaV2PocCertificationTest.hinglishDateCorrectionRetainsProviderAndInvalidatesSlot` | Expected provider name `Doc Akshu Kumar`; actual `null`. | Conversation service → canonical provider resolution and date correction. | **C. TEST_FIXTURE_DRIFT** — provider is not canonically adopted in the fixture before the date-correction assertion. | YES |
| `AivaV2PocCertificationTest.sameConversationIdentifierCannotReuseDraftAcrossTenantScopes` | Expected first tenant’s provider `Doc Akshu Kumar`; actual `null` before the cross-tenant assertion. | Conversation service → tenant-scoped provider adoption/session key. | **C. TEST_FIXTURE_DRIFT** — initial fixture fails provider adoption, so the cross-tenant isolation behavior is not reached. | YES (intended assertion is tenant isolation) |
| `AivaV2AvailabilityServiceBoundaryIntegrationTest` | Initial run: Docker unavailable. Rerun: 1/1 passed. | Spring context, Testcontainers PostgreSQL, booking tools → explicit-tenant `doctorSlotsForTenant`. | **J. INFRASTRUCTURE_TEST** for the initial run only; rerun passed. | YES; now covered in the focused rerun |
| `AivaV2RealTransactionalReliabilityIntegrationTest` | Initial run: Docker unavailable after prior Docker detection failure. Rerun: 1/1 passed. | Spring + PostgreSQL/Redis Testcontainers; transaction/idempotency reliability. | **J. INFRASTRUCTURE_TEST** for the initial run only; rerun passed. | YES; now covered in the focused rerun |

The 13 persistent assertion failures are the first 13 rows above. The two infrastructure errors passed on rerun and are not counted as current code failures.

## 3. Failure groups and recommended phase

| Root-cause group | Affected tests | Classes / area | Type | Phase recommendation |
|---|---|---|---|---|
| Provider phrase cleanup and short-name/catalog matching | `pendingProviderFieldTreatsShortDoctorPhrases...`; `fullBookingConversation...`; `serviceSessionKeys...`; `standaloneControlTurns...` | Language adapters, gateway pending-provider extraction, provider resolver/tools | Local provider-discovery behavior; some tests also have catalog-name mismatch | **Phase 1** captures language-boundary behavior; only change matching under Phase 3 provider/context transitions. First align the fixtures to their intended canonical provider identity during failure triage, without weakening tenant checks. |
| Canonical provider fixture does not satisfy transactional boundary | `providerResolutionAdvancesWorkflow...`; `callToBookNeverInvokesAvailabilityOrBooking`; `englishHappyPath...`; `hinglishDateCorrection...`; `sameConversationIdentifier...` | `AivaV2TransactionalKernel`, `AivaV2BookingTools.adoptCanonicalBookingProvider`, certification fixtures | Primarily fixture drift; may conceal real behavior until provider is resolved | **Phase 0 follow-up triage / Phase 3**. Separate UUID tenant/canonical capability fixture failures from product behavior before using these tests as Phase 1 gates. |
| Deterministic route invalidates gateway mock expectation | `activeBookingContextIncludesBoundedDraftValues...` | Gateway fast path vs test’s expected AI invocation | Test/architecture contract mismatch | **Phase 1 or 6**. Assert the typed decision/context outcome, not that an LLM was called for a deterministic utterance. |
| Provider honorific expected in semantic patch | `answerEnvelopeIsAccepted` | Gateway parse/canonical name normalization | Stale expectation unless the product contract requires honorific preservation | **Phase 1** document and settle normalized-vs-display name contract. |
| Hindi affirmative confirmation route | `hindiAndEnglishConfirmationHaveIdenticalKernelSemantics` | Language adapter/gateway/kernel | Language/control behavior | **Phase 5** owns final confirmation. Keep this test as a required baseline guard; do not broaden confirmation during Phase 0. |
| Reschedule page result | `rescheduleConversationUsesRealServiceTools...` | Reschedule service tools | Separate workflow | **Phase 8 or its own reschedule workstream**; it does not block BOOK simplification. |
| Testcontainers socket | Two integration classes, both pass on authorized rerun | Local test infrastructure | Environment only | Resolve CI/local Docker access before relying on these tests as a gate; not a production defect. |

## 4. BOOK impact

Direct or indirect BOOK-related baseline failures: **11 assertion failures** (the gateway context expectation; two phrase/provider behavior failures; provider resolution; three vertical provider/continuation failures; and five certification failures). This count includes fixture-drift tests that do not reach their intended assertion, so it is not a count of 11 independently proven runtime defects.

One failure is reschedule-only. The gateway honorific test does not affect booking identity. The two infrastructure errors passed on rerun. Before Phase 1, treat all 13 persistent failures as known baseline observations; do not quietly update expectations or fixtures as part of language changes.

## 5. Current runtime entry points

### A. DotVoice inbound

```text
DefaultDotVoiceMediaStreamClient
→ CommunicationTestDotVoiceInboundHandler
→ CommunicationTestService.processInboundFirstUtterance
→ VoiceOrchestratorService.transcribeBufferedAudio
→ VoiceReadOnlyAivaService.continueTurn
→ AivaV2VoiceConversationAdapter
→ language/control handling
→ deterministic adapter routing or AiAivaV2ConversationDecisionGateway
→ AivaV2TransactionalKernel / read-only gateway
→ AivaV2BookingTools / PatientPortalService / AppointmentService
→ façade/adapter response
→ CommunicationTestService.playInboundAudio
→ ReminderVoiceRenderer / TTS
```

The real inbound test is `AivaV2VoiceDotVoiceInboundPathTest`, which enters through the `CommunicationTestService` final-transcript path with a simulated inbound context; it does not validate real acoustic STT quality. `CommunicationTestServiceTest` and `CommunicationTestWiringContextTest` cover parts of the communication path. `CommunicationTestDotVoiceInboundHandler` and the provider media client have lifecycle/audio framing tests.

### B. Alternate Voice connector

```text
VoiceOrchestratorService.processAivaV2FinalTranscript
→ AivaV2VoiceTurnConnector.submitFinalTranscript
→ AivaV2ConversationService.message
→ AivaV2SessionStore
→ AiAivaV2ConversationDecisionGateway
→ AivaV2TransactionalKernel / AivaV2BookingTools
```

`AivaV2VoiceTurnConnectorTest`, `AivaV2ConversationService` vertical tests, and many kernel/gateway tests use this or a direct service route. They do **not** by themselves prove `VoiceReadOnlyAivaService` or the DotVoice inbound handler path. `VoiceOrchestratorServiceTest` and `VoiceTestWebSocketHandlerTest` are generic/legacy Voice path tests; they are not substitutes for the DotVoice AIVA V2 test.

### C. Web

```text
Patient Portal authenticated request
→ PatientPortalService Web adapter
→ AivaV2ConversationService / session store for conversational requests
→ gateway/kernel/tools where used
→ AppointmentBookingApplicationService
→ AppointmentService
```

`PatientPortalServiceTest` covers Web ownership and service behavior. It does not prove Voice trust/context creation. The shared application/domain booking boundary is common only where the flow enters that boundary.

## 6. Current orchestration ownership snapshot

| Owner | Intent/control interpretation | Workflow/state writes | AI | Business calls | Rendering |
|---|---|---|---|---|---|
| `VoiceReadOnlyAivaService` | Decides whether auth/guest/registered booking/read-only route applies; checks contextual booking and relevant continuation. | Reads/writes authentication and guest states through adapter; may invoke booking preparation after a first adapter result. | Indirect via adapter/gateway. | Policy, OTP, registration, `HealthcareAiReadOnlyToolGateway`, execution-context factory. | Auth/guest copy, read-only result formatting, response overrides. |
| `AivaV2VoiceConversationAdapter` | Language normalization, booking phrase/continuation controls, provider/date/time facts, slot controls, confirmation recognition. | Major session/projection updates; active doctor, booking draft synchronization, confirmation lifecycle. | Calls decision gateway on unresolved turns. | Tool executor, transactional boundary, `VoiceBookingExecutionService`. | Booking responses and multiple local response helpers. |
| `AiAivaV2ConversationDecisionGateway` | Ordered deterministic fast paths plus model extraction; selects operations and can propose patches. | Does not persist, but emits workflow-changing operation/patch decisions. | Yes: generic structured extraction fallback through `AiOrchestrationService`. | No direct domain mutation. | No primary render, but supplies response language/confidence. |
| `AivaV2TransactionalKernel` | Interprets operation against current draft and pending transaction state. | Creates/replaces drafts; applies patches; advances availability, selection, confirmation and other transaction workflows. `START_BOOKING` can reset a draft if upstream normalization fails to convert a continuation. | No. | `AivaV2BookingTools`. | Categories, structured response, some raw assistant text. |
| `AivaV2BookingTools` | Provider resolution/canonical adoption, bookability checks, availability and selected-slot validation. | No session store; returns results that drive transitions. | No. | Tenant provider lookup, `PatientPortalService`, canonical slot APIs. | Some tool error strings are English. |
| `AivaV2SessionStore` | No NLU. | Persists `SessionProjection`, presentation metadata and turn receipts; serializes/reserves Redis updates. | No. | Redis or in-memory test store. | No. |

Current session writes are in the adapter and kernel under store-mediated updates. The gateway is a decision producer, not a persistence owner. `SessionProjection.activeWorkflow()` is derived from pending transaction/draft fields; it is not a separate gateway-owned workflow flag.

## 7. Hindi/Hinglish regression corpus inventory

Inventory only. The “future action” column is the intended normalized meaning for later phases; it does not assert that the current parser already returns that action. Clarification is allowed where the phrase is ambiguous or cannot be matched to canonical state.

| Group / phrase | Expected contextual meaning → future normalized action | Clarification allowed? | Meaningful state |
|---|---|---:|---|
| BOOK START: `डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए` | Start booking, provider evidence Amit → `REQUEST_BOOKING` + `PROVIDE_PROVIDER` | Yes, if provider ambiguous | Any; especially `BROWSING` |
| BOOK START: `Amit Verma ka appointment chahiye` | Start booking with Amit → `REQUEST_BOOKING` + `PROVIDE_PROVIDER` | Yes | Any; especially `BROWSING` |
| BOOK START: `Dr Amit ka appointment book karna hai` | Start booking, short provider evidence → `REQUEST_BOOKING` + `PROVIDE_PROVIDER` | Yes, if canonical provider not unique | Any; especially `BROWSING` |
| DATE: `आज` | Today in tenant timezone → `PROVIDE_DATE` | Yes, only if current policy/date constraints make it invalid | `AWAIT_DATE` or future-availability query |
| DATE: `कल` | Contextual date per current workflow and tenant timezone → `PROVIDE_DATE` | Yes outside a clearly forward-looking date request | `AWAIT_DATE`, future slots, or existing contextual date state |
| DATE: `परसों` | Day after tomorrow → `PROVIDE_DATE` | Yes if unsupported/ambiguous in actual locale | `AWAIT_DATE` or date refinement |
| DATE: `kal ka` | Hinglish contextual date → `PROVIDE_DATE` | Yes if unresolved | `AWAIT_DATE` or date refinement |
| DATE: `parso ka` | Hinglish day-after-tomorrow → `PROVIDE_DATE` | Yes if unresolved | `AWAIT_DATE` or date refinement |
| DAYPART: `सुबह` | Morning filter → `SET_TIME_PREFERENCE(MORNING)` | Yes if no availability remains | `AWAIT_DAYPART` or availability refinement |
| DAYPART: `शाम` | Evening filter → `SET_TIME_PREFERENCE(EVENING)` | Yes if no availability remains | `AWAIT_DAYPART` or availability refinement |
| DAYPART: `evening` | Evening filter → `SET_TIME_PREFERENCE(EVENING)` | Yes if no availability remains | `AWAIT_DAYPART` or availability refinement |
| DAYPART: `shaam mein` | Hinglish evening filter → `SET_TIME_PREFERENCE(EVENING)` | Yes if no availability remains | `AWAIT_DAYPART` or availability refinement |
| SHOW MORE: `और स्लॉट बताओ` | Next page of retained slots → `SHOW_MORE` | No when a current slot page exists; otherwise yes | `AWAIT_SLOT` with current availability page |
| SHOW MORE: `और स्लॉट दिखाओ` | Next page of retained slots → `SHOW_MORE` | Same | `AWAIT_SLOT` |
| SHOW MORE/STT: `और प्लॉट बताओ` | Likely STT substitution for “slot”; `SHOW_MORE` only with current slot page | Yes outside that context | `AWAIT_SLOT` |
| SHOW MORE: `अगला स्लॉट बताओ` | Next displayed page, not a new availability search → `SHOW_MORE` | Yes if page context absent | `AWAIT_SLOT` |
| SHOW MORE: `नेक्स्ट स्लॉट बताओ` | Hinglish next page → `SHOW_MORE` | Yes if page context absent | `AWAIT_SLOT` |
| SHOW MORE: `next slot` | Next page if displayed choices remain → `SHOW_MORE` | Yes if no current page | `AWAIT_SLOT` |
| SHOW MORE: `next slot batao` | Hinglish next page → `SHOW_MORE` | Same | `AWAIT_SLOT` |
| SHOW MORE: `aur dikhao` | More of the current displayed result → `SHOW_MORE` | Yes if no page is active | `AWAIT_SLOT` or a paged read-only result |
| SHOW MORE: `more slots` | Next page of current result → `SHOW_MORE` | Yes if page context absent | `AWAIT_SLOT` or paged read-only result |
| SLOT: `पहला` | First currently displayed canonical slot → `SELECT_SLOT` | Yes if no page/ambiguous | `AWAIT_SLOT` |
| SLOT: `दूसरा` | Second displayed canonical slot → `SELECT_SLOT` | Same | `AWAIT_SLOT` |
| SLOT: `first one` | First displayed canonical slot → `SELECT_SLOT` | Same | `AWAIT_SLOT` |
| SLOT: `second one` | Second displayed canonical slot → `SELECT_SLOT` | Same | `AWAIT_SLOT` |
| SLOT: `5:30` | Select only if uniquely matching an offered canonical slot → `SELECT_SLOT` | Yes if none/multiple match | `AWAIT_SLOT` |
| SLOT: `साढ़े पांच` | 5:30 local-time evidence; select only against current choices → `SELECT_SLOT` | Yes if none/multiple match | `AWAIT_SLOT` |
| CONFIRM: `हाँ` | Affirm the pending booking confirmation → `CONFIRM` | Yes unless bound confirmation is active | `AWAIT_CONFIRMATION` |
| CONFIRM: `हाँ बुक कर दो` | Explicit booking affirmative → `CONFIRM` | Yes unless bound confirmation is active | `AWAIT_CONFIRMATION` |
| CONFIRM: `kar do` | Hinglish affirmative only in pending-confirmation context → `CONFIRM` | Yes outside that context | `AWAIT_CONFIRMATION` |
| CONFIRM: `yes` | Affirm pending booking confirmation → `CONFIRM` | Yes unless pending confirmation active | `AWAIT_CONFIRMATION` |
| CONFIRM: `book kar do` | Booking affirmative, not general authentication → `CONFIRM` | Yes unless pending confirmation active | `AWAIT_CONFIRMATION` |
| PROVIDER CHANGE: `Ravi doctor dikhao` | Explicit Ravi provider request/query → `PROVIDE_PROVIDER` or `QUERY` by context | Yes if Ravi ambiguous | Any; especially active booking/provider context |
| PROVIDER CHANGE: `डॉक्टर बदलो` | Request provider change, target missing → `PROVIDE_PROVIDER` request | Yes; must ask for target | Active booking with provider bound |
| PROVIDER CHANGE: `Amit ki jagah Ravi` | Replace current provider with Ravi → `PROVIDE_PROVIDER` | Yes if Ravi is not unique | Active booking/provider context |
| PROVIDER CHANGE: `mujhe doosre doctor ka slot chahiye` | Change provider then retain date criteria → `PROVIDE_PROVIDER` | Yes, ask which provider | Active booking or availability context |
| STT ambiguity: `slot` → `plot` / `प्लॉट` | Slot control only in a valid slot-page context | Yes outside it | `AWAIT_SLOT` |
| STT ambiguity: `डॉक्टर अमित वर्मा` → `Dr Amita Varma` | Provider identity evidence requires canonical resolver; never silently bind a different doctor | **Yes** if resolver cannot uniquely confirm | Provider request/change |
| STT high-risk: `Amit` / `Dr Amit` | Partial name evidence; resolve only against tenant candidates | **Yes** if not unique | `AWAIT_PROVIDER` or explicit provider request |

Additional live-call STT substitutions should be appended with call/turn correlation and a redacted transcript reference. Do not store caller phone, OTP, or unnecessary patient details in this corpus.

## 8. Protection invariants

These are acceptance conditions for all later phases. They document current intended contracts; Phase 0 does not assert that every failing test currently proves them end to end.

### Tenant

- Tenant scope is explicit from call/session context through provider adoption, availability, execution context, and mutation.
- Do not infer tenant from doctor/public-discovery records, request thread-locals, clinic guesses, or booking references.
- Every availability and mutation query is tenant scoped.

### Provider

- Public discovery output is identity evidence, not executable booking capability.
- Before `BookingDraft.selectedProvider` is set, provider identity is validated against the canonical tenant-scoped source.
- Tenant-local doctors use `CARE_PRIVATE` and local scheduling semantics.
- External providers retain their true booking target/reference and validation path.
- Provider changes are atomic across active doctor context and booking draft; ordinary date/slot continuations preserve provider identity.

### Availability

- Local availability uses explicit tenant + canonical provider/doctor ID + local date/timezone through `PatientPortalService.doctorSlotsForTenant` and `AppointmentService.listSlots`.
- Availability display is read-only and does not grant personal appointment access or mutation rights.
- `SHOW_MORE` pages the retained availability result and does not reload it.
- Canonical slot references come from that retained availability result.

### Selection

- Ordinal/time selection resolves only against currently displayed canonical choices.
- User text cannot synthesize a slot or create a replacement slot reference.
- Ambiguous or absent matches preserve the choices and ask a bounded clarification.

### Confirmation

- Confirmation is bound to patient/tenant/call context, draft identity and revision, provider, date/time, canonical slot reference, availability lineage, and expiry.
- A material draft change invalidates its prior confirmation.
- Final mutation requires an explicit affirmative tied to the currently prepared draft.

### Mutation

- `VoiceBookingExecutionService` remains the Voice execution bridge.
- `AppointmentBookingApplicationService` remains the shared booking mutation boundary.
- `AppointmentService.createScheduled` remains the appointment-domain mutation path.
- Idempotency and domain conflict/capacity validation prevent duplicate appointments.
- A genuinely stale/unavailable selected slot remains rejected; never substitute another slot silently.
- Audit actor and interaction/correlation metadata remain truthful.

### Authorization

- Caller phone matching is identification evidence, not general authentication.
- OTP/preverified/guest/restricted booking authorization remains explicit, scoped, expiring, and call bound as applicable.
- Guest or restricted new-booking permission never grants lookup, clinical data, cancellation, or rescheduling.

### State

- Repeating booking intent must not reset an active booking draft.
- Unknown/ambiguous input preserves safe canonical provider/date/availability state.
- Criteria changes invalidate dependent availability/selection/confirmation state, not unrelated canonical provider identity.
- Session key remains tenant and call scoped; call end clears runtime authorization.

### Presentation

- `presentationMode` is persisted in the session and is intended to survive workflow turns.
- Phase 0 only records this behavior; renderer/TTS inconsistency is not addressed here.

## 9. Orchestration responsibility snapshot

| Class/service | Intent interpretation | Control normalization | Workflow transitions | State writes | AI usage | Business calls | Rendering |
|---|---|---|---|---|---|---|---|
| `VoiceReadOnlyAivaService` | Selects auth, guest, read-only, or booking branch; tests contextual booking continuation. | OTP/guest consent parsing plus adapter prechecks. | Can route into adapter and then separately start booking preparation for the same turn. | Auth/guest state through adapter/session; consumes result. | Indirect through V2 adapter/gateway. | Policy, OTP, guest registration, read-only gateway, booking context/execution service. | Read-only/auth/guest/error responses and overrides. |
| `AivaV2VoiceConversationAdapter` | Language facts, provider/date/time/slot/confirmation controls. | Main contextual recognizer; pre-normalizes slot-page controls. | Owns read-only flow and booking preparation entry logic. | Updates session projection, draft, active doctor, confirmation. | Calls gateway for unresolved interpretation. | Read-only tool executor, transactional boundary, Voice booking execution. | Adapter response methods and booking messages. |
| `AiAivaV2ConversationDecisionGateway` | Deterministic fast paths plus model-decided operation/patch on fallback. | Has its own control/selection/pagination/recovery rules. | Emits operation, topic action, patch; can indirectly change/reset state through kernel. | No direct persistence. | Generic structured extraction through AI orchestration. | None directly. | Language/confidence/decision payload only. |
| `AivaV2TransactionalKernel` | Interprets operation against draft/pending transaction state. | Operation-specific decision/result checks. | Creates/replaces draft; advances provider/date/availability/selection/confirmation flows. | Replaces `SessionProjection`, draft, results and pending confirmation. | None. | `AivaV2BookingTools`, other transaction tools. | Response category/structured response and some text. |
| `AivaV2BookingTools` | Resolves provider evidence and bookability; interprets tool criteria. | Slot/time filtering and tool-level validation. | No conversation FSM, but results advance kernel state. | No session persistence. | None. | Patient Portal provider and availability methods; canonical slot validation. | Some English tool error text. |
| `AivaV2SessionStore` | None. | Turn admission/replay protocol, not language controls. | Persists snapshots/reservations/receipts. | Redis or memory state and separate presentation metadata. | None. | Redis. | None. |

## 10. Protected service boundaries

Unless a later phase explicitly identifies an invariant-level blocker and obtains scope approval, do not change these during Phase 1 or Phase 2:

- `AppointmentService` and appointment-domain lifecycle/capacity/overlap/audit/event behavior.
- `AppointmentBookingApplicationService` validation and shared mutation contract.
- `VoiceBookingExecutionService` confirmation binding and execution semantics.
- `BookingExecutionContextFactory`, `VoiceTransactionPolicyResolver`, capability gate, and authorization semantics.
- Patient/tenant ownership checks, truthful audit actor, transaction idempotency.
- `PatientPortalService.doctorSlotsForTenant` tenant-explicit availability contract and `AppointmentService.listSlots`.
- Canonical stale-slot revalidation and stable canonical slot-reference format.

If a failure appears to require touching one of these, record the failing invariant and stop for explicit scope review. The orchestration phases may change which typed action reaches these boundaries, but must not weaken their contracts.

## 11. Known unresolved risks before Phase 1

1. **Thirteen assertion failures remain.** Provider resolution/candidate fixtures dominate; the Hindi confirmation failure directly affects BOOK. Phase 1 must not absorb or silently “fix” these unrelated assertions.
2. **Provider corpus aliases need a clear contract.** `Dr Akshu` vs `Doc Akshu Kumar`, filler prefixes like `Talk`, and honorific preservation are not consistently specified by current tests.
3. **Several tests stop before their intended invariant.** Provider-not-found masks session isolation, call-to-book, and confirmation behavior. Tests need separately valid canonical provider fixtures before they can certify later assertions.
4. **Integration tests need Docker access.** They pass when Testcontainers can reach local Docker, but the unprivileged run cannot start them. CI should provide Docker or report these as infrastructure-skipped rather than application failures.
5. **The worktree is already substantially modified.** This baseline represents the actual current tree, not a clean committed revision. Phase 1 review must preserve existing unrelated edits and capture the starting diff.
6. **Alternate connector tests are not DotVoice proof.** Keep the service-path distinction explicit in every future gate.
7. **Presentation state is persisted but not end-to-end authoritative.** It is outside Phase 1 scope; retain current response/TTS behavior as baseline and schedule the cleanup separately.
8. **Phase 1 readiness:** safe to begin only as a bounded interpretation-boundary exercise with the corpus and 13 known failures recorded as baseline. Do not use a green-suite claim as the gate; require no new failures in the focused language/DotVoice tests and preserve the provider/auth/business boundary contracts above.

## 12. Phase 0 change record

- Created this baseline/protection document only.
- No production source, tests, configuration, migration, runtime, or Docker image was changed by Phase 0.
- The two Testcontainers reruns used temporary test containers; images were not rebuilt.
- No migration was added.
- Validation command and output are recorded in Section 1.
