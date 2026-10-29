# AIVA V2 Voice Phase 6 — Orchestration Consolidation

**Status:** Phase 6 implementation complete; Phase 7 not started.
**Owner:** AIVA V2 Voice application orchestration in `api-bff`.
**Persistence, migration, and frontend:** None planned.

## Placement and dependency plan

This is a Voice adapter consolidation. The change is limited to the existing Voice façade/adapter boundary, focused inbound and alternate-path tests, and this specification. The Voice adapter may continue to use the existing language adapters, canonical provider/availability boundary, `AivaV2TransactionalKernel`, transaction authorization boundary, and booking execution services. No domain service, persistence implementation, schema, migration, or frontend change is planned. The shared gateway remains in place for Web and the alternate Voice connector.

## Before authority map

| Concern | Current authorities | Target authority | Duplicate branches | Safe removal scope |
|---|---|---|---|---|
| Voice booking start | `VoiceReadOnlyAivaService` explicit-start and active-continuation gate; adapter parser; gateway fast path; kernel restart guard | Façade performs policy/context entry only; adapter decides active-context action | `isRelevantVoiceBookingContinuation` duplicates adapter context recognition | Remove façade continuation classifier after tests prove active-flow delegation |
| Provider interpretation | language adapters, Voice adapter evidence resolution, shared gateway extraction | Adapter/canonical provider boundary for authorized Voice | Generic gateway provider and continuation fast paths | Retain for Web/alternate connector; no global removal |
| Collection (provider/date/daypart) | `AivaV2VoiceCollectionContext` and adapter; gateway patches; kernel draft patching/guard | Adapter selects authorized Voice action; kernel applies it | Gateway independently classifies generic collection operations | Keep shared gateway path for non-authorized adapter callers |
| Slot continuation/selection | `AivaV2VoiceSlotContext`, adapter resolver; gateway slot controls; kernel applies page/selection | Adapter action for authorized Voice | Shared gateway independently parses pagination/ordinal/time | Retain for Web/alternate connector; isolate by call path |
| Provider switch | adapter and `AivaV2VoiceContextTransitions`; kernel guard/transition | Adapter pending switch and canonical adoption | Kernel still protects generic decision callers | Keep kernel guard as invariant; no second Voice commit path |
| Confirmation | adapter `AivaV2VoiceConfirmationContext` and execution service; generic gateway polarity; kernel confirm | Authorized adapter only for booking mutation | Generic kernel/gateway confirmation behavior | Retain compatibility for Web; adapter remains isolated from it |
| Clarification/response | language adapter, Voice adapter, gateway UNKNOWN, kernel response, façade mapping | Authorized adapter result is the one Voice booking response; generic service renderer remains for alternate connector/Web | Adapter/kernel can both create fallback text | Remove only façade-level re-routing; preserve distinct renderer path |
| AI fallback | authorized adapter calls gateway after deterministic context; generic gateway fast path/schema | Adapter context decision first; gateway proposal only after it declines to decide | Shared gateway schema includes workflow operations | Keep shared schema for Web/alternate connector; reject incompatible Voice proposal before kernel |
| State/context | `SessionProjection`, `BookingDraft`, `AvailabilityResult`, `VoiceClarificationState`, pure context helpers | Existing state owners; helpers derive logical context | Legacy workflow/pending descriptions overlap derived state | No persisted-field removal in Phase 6 |

## Target path and compatibility

Authorized DotVoice: `VoiceReadOnlyAivaService` authenticates/authorizes and selects the authorized booking context. If that session already has a booking workflow, it delegates every non-empty transcript to `AivaV2VoiceConversationAdapter`; it does not decide whether the utterance is relevant to the draft. With no active workflow, the existing explicit booking-start/policy gate remains. The adapter applies completed-outcome and pending clarification/provider-switch/confirmation/slot/collection precedence, then emits one accepted operation to `prepareOnly` or the existing authorized execution boundary. The kernel applies a validated state transition; existing business services remain the only owners of provider, availability, and booking effects.

Alternate Voice: `VoiceOrchestratorService.processAivaV2FinalTranscript` → `AivaV2VoiceTurnConnector` → `AivaV2ConversationService` → shared gateway/temporal clarification/execution boundary/kernel/structured renderer. Web uses this same generic conversation route. Gateway deterministic fast paths and kernel compatibility behavior are retained because these callers do not yet cross the authorized Voice adapter contract. No attempt is made to merge their transaction authority in this phase.

## Before/after precedence

The authorized adapter checks completed outcome, then the one persisted pending clarification variant (provider-switch is a tagged variant of that same state), then confirmation/slot/collection context, deterministic continuation, and only then the gateway fallback. This is equivalent to the intended clarification-before-switch precedence because the session has one clarification owner; the two pending states cannot coexist. Phase 6 makes the adapter the entry route for every non-empty turn while an active draft, pending confirmation, or clarification exists. The adapter emits one `VOICE_CONVERSATION_DECISION` record and one `acceptedDecisionCount` diagnostic. Receipt reconciliation can preempt a repeated confirmation when a durable outcome exists.

## Compatibility classification

- `AiAivaV2ConversationDecisionGateway.fastPath(...)` slot/collection/confirmation branches: **COMPATIBILITY — Web and alternate Voice connector**; not removed while `AivaV2ConversationService` owns those callers.
- `AivaV2TransactionalKernel` generic provider-change/restart/confirm guards: **KEEP — generic-channel defense/compatibility**; the authorized Voice adapter supplies bounded decisions and `prepareOnly` remains non-mutating.
- `AivaV2VoiceCollectionContext`, `AivaV2VoiceSlotContext`, `AivaV2VoiceConfirmationContext`, `AivaV2VoiceContextTransitions`: **KEEP — pure predicates/transforms**, no business calls or rendering.
- `VoiceReadOnlyAivaService` active-flow relevance filter and adapter `isRelevantVoiceBookingContinuation` / `isContextualVoiceBookingRequest`: **REMOVED** from the authorized path. A single adapter-owned `shouldRouteVoiceBookingTurn` now handles entry eligibility and active workflow routing. The narrow generic read-only exclusion remains in that adapter entry classifier.
- Explicit-start recognition with no active flow: **KEEP** as an entry eligibility check; it does not interpret an existing draft turn.

## Baseline classification before implementation

Accepted baseline has twelve failures. Their identities are enumerated in the Phase 4 specification. Current classification pending targeted inspection: one generic gateway fixture/contract assertion; two provider-phrase/provider-fixture assertions; four vertical language/reschedule/session expectations; five POC fixture/legacy expectation assertions. No baseline test will be weakened merely to reduce the count. Any changed identity or assertion must have a production or fixture-contract explanation.

## Planned files and effects

- `VoiceReadOnlyAivaService.java`: remove the active-draft continuation filter; retain auth/policy and explicit start behavior.
- `AivaV2VoiceConversationAdapter.java`: remove the now-unused continuation-recognition API and any helpers exclusive to it; emit a single accepted-decision trace for authorized turns.
- `AivaV2VoiceDotVoiceInboundPathTest.java` and focused adapter/service tests: assert active booking turns reach the adapter, deterministic actions make no AI call, and one operation is accepted.
- `AivaV2VoiceTurnConnectorTest.java`, `AivaV2ConversationServiceTest`/vertical tests as present: prove alternate Voice/Web gateway compatibility.
- This specification: update with final authority map, branch classification, failure mapping, test evidence, and limitations.

No gateway/kernel branch is planned for deletion until call-site analysis proves it is authorized-Voice-only. Provider/draft/security state and session serialization remain unchanged.

## After authority map

| Concern | Authorized DotVoice authority after Phase 6 | Compatibility kept | Safe removal outcome |
|---|---|---|---|
| Booking entry / active continuation | `AivaV2VoiceConversationAdapter.shouldRouteVoiceBookingTurn` selects the adapter path; the adapter owns the contextual decision | Read-only/global turns still flow through `VoiceReadOnlyAivaService` read-only routing | Removed the façade's duplicate start-plus-continuation boolean classifier |
| Provider / collection / slot / switch / confirmation | Adapter context helpers and one adapter decision before kernel/effect | Shared gateway fast paths and generic kernel guards remain for Web and alternate connector callers | No authorized Voice duplicate gateway decision runs after adapter selection; shared branches not deleted because they are channel-agnostic |
| AI fallback | Adapter calls shared gateway only after contextual/deterministic handling yields no decision; validates allowed operation before kernel | Shared gateway schema remains available to Web/alternate connector | No change to global schema; incompatible Voice proposals remain rejected by existing context guards |
| Response intent | Adapter result is authoritative for clarification, provider-switch, identity and slot-exhaustion intent; façade maps it without relabeling as generic booking | Generic renderer remains for alternate Voice/Web | Removed a façade result-label override for those outcomes |
| Session/context state | Existing projection/draft/availability/clarification fields and pure context helpers | Persisted fields and serializers stay compatible | No field/persistence changes |

The pure helpers remain decision predicates/transforms only. They do not call business services, render, or invoke AI.

## Baseline classification and validation

The authoritative selector was rerun with existing Docker access and no service rebuild/restart. It ran **572 tests: 560 passed, 12 known assertion failures, 0 errors, 0 skipped**. The 571 accepted Phase 5 tests are unchanged; one new passing route-boundary test accounts for the additional test. Testcontainers availability boundary passed 1/1, transactional reliability passed 9/9, and distributed session storage passed 12/12. The sandbox-only attempt could not access Docker and returned two infrastructure errors; those were cleared by the authorized daemon-access retry.

| Accepted Phase 5 failing test | Phase 6 result | Classification |
|---|---|---|
| `AiAivaV2ConversationDecisionGatewayTest.activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty` | Still fails unchanged | Shared gateway expectation of AI invocation despite deterministic fast path; compatibility path, not cleanup-owned |
| `AivaV2ActiveBookingFollowUpRegressionTest.pendingProviderFieldTreatsShortDoctorPhrasesAsAuthoritativeResolverCandidates` | Still fails unchanged | Legacy shared gateway phrase extraction retains “Talk”; compatibility branch retained for Web/alternate Voice |
| `AivaV2ActiveBookingFollowUpRegressionTest.providerResolutionAdvancesWorkflowWithoutLosingRequestedDate` | Still fails unchanged | Fixture does not satisfy canonical tenant provider resolution |
| `AivaV2LanguageBoundaryVerticalTest.fullBookingConversationUsesRealAdapterGatewayKernelToolsAndRendererInAllStyles` | Still fails unchanged | Short-name fixture does not canonically resolve a provider |
| `AivaV2LanguageBoundaryVerticalTest.rescheduleConversationUsesRealServiceToolsAndStructuredResponsesInAllStyles` | Still fails unchanged | Reschedule pagination expectation; outside the authorized booking adapter migration |
| `AivaV2LanguageBoundaryVerticalTest.serviceSessionKeysIsolatePatientConversationTenantAndPresentation` | Still fails unchanged | Same invalid canonical provider fixture prevents reaching isolation assertion |
| `AivaV2LanguageBoundaryVerticalTest.standaloneControlTurnsKeepEstablishedConversationPresentationThroughService` | Still fails unchanged | Short-name fixture does not canonically resolve a provider |
| `AivaV2PocCertificationTest.callToBookNeverInvokesAvailabilityOrBooking` | Still fails unchanged | Provider fixture fails canonical adoption before capability assertion |
| `AivaV2PocCertificationTest.englishHappyPathMoreSlotsContextQuestionAndDuplicateConfirmation` | Still fails unchanged | Provider fixture fails canonical adoption |
| `AivaV2PocCertificationTest.hindiAndEnglishConfirmationHaveIdenticalKernelSemantics` | Still fails unchanged | Legacy generic-kernel confirmation expectation; not the authorized Voice confirmation path |
| `AivaV2PocCertificationTest.hinglishDateCorrectionRetainsProviderAndInvalidatesSlot` | Still fails unchanged | Provider fixture fails canonical adoption before date refinement |
| `AivaV2PocCertificationTest.sameConversationIdentifierCannotReuseDraftAcrossTenantScopes` | Still fails unchanged | Provider fixture fails before the intended tenant-isolation assertion |

No new persistent failure was observed. Phase 6 did not alter any of the twelve expectations or relax provider canonicalization.

## Test evidence

- DotVoice inbound: 51/51.
- Voice adapter: 74/74.
- Language adapter: 32/32.
- Transactional kernel: 42/42.
- Authorized Voice booking execution: 6/6.
- Alternate Voice connector: 2/2.
- Read-only Voice authentication: 7/7; schedule: 16/16.
- Communication test service: 30/30; controller: 11/11; wiring context: 1/1.
- Architecture tests: 7/7.
- Shared gateway: 78/79; its sole failure is the accepted baseline above.
- Language vertical: 9/13; its four failures are the accepted baseline above.
- Broad authoritative selector: 572 run, 560 pass, 12 accepted failures, 0 errors, 0 skipped.
- `git diff --check`: passed.

The focused selector also passed all Phase 1–5 owned adapter, slot, provider-switch, confirmation, DotVoice and alternate-connector tests. The five failures in that selector were the same four language-vertical fixtures and one gateway expectation listed above.

## Compatibility branches and cleanup scope

- `AiAivaV2ConversationDecisionGateway.fastPath(...)` and generic `AivaV2TransactionalKernel` operations remain **COMPATIBILITY — Web and alternate Voice connector**. Their callers still enter through `AivaV2ConversationService`; no channel discriminator currently allows safe removal for DotVoice alone.
- Kernel workflow/restart, provider and confirmation guards remain transaction invariants/compatibility defenses. No Voice turn is allowed to route around adapter validation into a new mutation path.
- Legacy read-only schedule and discovery rendering remains separate by design.
- Persisted workflow fields, candidate metadata, pending confirmation, completed outcome, pagination fields and presentation metadata are **KEEP** for serialization compatibility; logical context remains derived. No state field was removed or re-owned.
- Provider representation remains: `activeVoiceDoctor` is conversational identity, `BookingDraft.selectedProvider` is transaction binding, persisted clarification candidates are bounded canonical identities, and `latestAvailabilityResult` is snapshot evidence. No redundant provider synchronization or canonicalization path was added.
- Code-size effect for this scoped change: removed two routing entry classifiers (about 65 lines) and their duplicated façade combination; added one combined adapter route predicate plus active-flow classifier (about 50 lines), one trace wrapper/intent mapping (about 35 lines), and focused assertions (about 10 lines). Approximate net source growth is 30 lines. Gateway fast paths removed: 0; compatibility branches removed: 0; duplicate kernel confirmation/provider/slot branches removed: 0. This is a narrow orchestration consolidation, not a net-deletion claim; broader gateway/kernel cleanup requires a channel boundary so Web/alternate Voice callers can be isolated.

Phase 7 presentation/rendering consolidation has not started. There is no Phase 6-specific dependency on appointment mutation, authorization, audit/idempotency, provider security, availability business behavior, or stale-slot services.
