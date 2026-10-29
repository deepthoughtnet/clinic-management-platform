# AIVA V2 Voice Phase 4 — Collection Context Boundary

**Status:** Phase 4 implementation and validation complete.
**Owner:** AIVA V2 Voice application orchestration in `api-bff`.
**Persistence/migrations/frontend:** None. Collection contexts derive from the existing `SessionProjection` and `BookingDraft`.

## Placement and dependencies

The implementation is limited to the Voice adapter, a pure logical-context helper, and narrow gateway/kernel safeguards and tests in `api-bff`. Provider resolution, provider adoption, and availability continue through existing canonical services and kernel tools. No domain service, mutation service, authorization/policy service, persistence owner, migration, or frontend area is changed.

## Logical collection contexts

The helper derives contexts only for an open `BOOKING` workflow with an active `COLLECTING` draft, valid patient/tenant binding, and no pending confirmation, temporal clarification, Voice clarification, cancellation/reschedule, or higher-priority workflow.

- `AWAIT_PROVIDER`: there is no canonical provider bound in the draft or active Voice provider binding.
- `AWAIT_DATE`: a canonical provider is bound and `preferredDate` is absent.
- `AWAIT_DAYPART`: provider and date are bound, no slot/confirmation is selected, no independent time preference is set, and the current tenant/provider/date/revision-bound availability snapshot has selectable slots spanning more than one daypart.
- Otherwise the collection context is `NONE`; `AWAIT_SLOT` remains owned by the Phase 2 predicate.

## Context refinement and allowed actions

An active draft is refined rather than restarted. Repeated booking words do not imply `START_BOOKING`. Collection recognition runs after pending clarification, provider-switch, and Phase 2 slot-context handling, but before gateway/global intent fallback. It produces one deterministic action for the current collection context or returns a contextual clarification without business calls.

`AWAIT_PROVIDER` admits canonical provider/specialty evidence, identity clarification, language/help/repeat/end, and abandon. `AWAIT_DATE` admits resolved date evidence, Phase 3 provider-switch routing, help/repeat/end, and abandon. `AWAIT_DAYPART` admits daypart, supported exact time/range, date change, Phase 3 provider-switch routing, help/repeat/end, and abandon. Ambiguous or unknown turns preserve the draft and ask a context-specific question.

## State and availability

First date collection retains the canonical provider and passes date through the existing kernel patch and availability path. A later date change retains tenant, patient, provider, and independent time preference while the kernel invalidates provider/date-bound availability, page, selected slot, and confirmation. Daypart/time refinement retains provider/date and uses the same patch/kernel path to refresh availability and clear dependent selection.

Availability is requested only when tenant, canonical provider, and date are present. Repeated booking intent while either provider or date is missing returns the existing missing-field question without provider discovery or availability. Kernel `continueFromDraft` remains the availability trigger/owner. After results are returned, the existing deterministic daypart narrowing asks only if the actual choices span multiple relevant dayparts; zero slots use existing no-availability recovery and a single daypart skips the question.

## AI and compatibility

Semantic AI remains a bounded proposal after deterministic/contextual recognition. It cannot reset an active draft, clear provider/date, switch provider, confirm mutation, or invent canonical evidence. Incompatible proposals are rejected and contextual clarification preserves session state.

The Voice adapter's collection decision is authoritative for `AWAIT_PROVIDER`, `AWAIT_DATE`, and `AWAIT_DAYPART`. Gateway date/daypart fast paths and kernel missing-field responses remain **COMPATIBILITY — PHASE 6 REMOVAL** for non-Voice callers or cases outside the derived collection contexts. Kernel patching and canonical tools remain authoritative for actual state changes and availability.

## Preserved state and context transitions

Repeated booking intent with a valid active draft is treated as continuation. If required provider/date evidence is still missing, the adapter asks for that field and does not trigger discovery, availability, or draft replacement. Deterministic date input refines `preferredDate`; date changes continue through the existing booking patch so provider, tenant, patient, and independent time preference are retained while the kernel invalidates the old availability result, display offset, selected slot, and confirmation binding. Daypart/exact-time/range input retains provider/date and reuses the same patch and availability path. No second date, daypart, exact-time, or range parser was introduced.

When multiple actual dayparts are represented in the valid availability snapshot, the adapter asks which daypart the patient prefers. A single represented daypart skips that question and presents the existing choices. An empty result uses the existing no-availability recovery. The explicit deterministic collection time-constraint path reuses the gateway's established parser without semantic AI and accepts only a bounded `GET_AVAILABILITY` refinement for the current provider/date.

Provider change requests remain owned by the Phase 3 provider-switch lifecycle. Collection recognition never directly changes the provider. Pending Phase 1 clarifications and Phase 3 switch confirmations retain their higher precedence over the collection predicates.

## Validation

Focused command:

```text
mvn -pl backend/api/api-bff -am test '-Dtest=AivaV2VoiceDotVoiceInboundPathTest,AivaV2VoiceConversationAdapterTest,AivaV2TransactionalKernelTest,AivaLanguageAdapterTest,AiAivaV2ConversationDecisionGatewayTest,CommercialCatalogArchitectureTest,CommercialPlatformArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Result: 272 tests ran; all Phase 4 DotVoice (38/38), adapter (74/74), kernel (42/42), language adapter (32/32), and architecture checks (7/7) passed. The sole assertion failure was the accepted gateway baseline test `activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty`.

The authoritative Phase 0/1/2/3 selector was rerun after implementation. Initial sandbox result: 546 tests, 12 assertion failures, 2 Testcontainers environment errors, 17 skipped. The assertion identities exactly match the accepted Phase 3 list below; no new persistent failures. Retrying the two integration classes against the existing Docker daemon (without rebuilding/restarting Docker) passed availability boundary 1/1 and transactional reliability 9/9. Effective result: 546 tests, 517 passed, 12 accepted baseline assertions failed, 0 errors, 17 skipped.

The 12 original assertion failures remain unchanged:

| Original Phase 0/3 failing test | Phase 4 status |
| --- | --- |
| `AiAivaV2ConversationDecisionGatewayTest.activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty` | Still failing; unchanged baseline |
| `AivaV2ActiveBookingFollowUpRegressionTest.pendingProviderFieldTreatsShortDoctorPhrasesAsAuthoritativeResolverCandidates` | Still failing; unchanged baseline |
| `AivaV2ActiveBookingFollowUpRegressionTest.providerResolutionAdvancesWorkflowWithoutLosingRequestedDate` | Still failing; unchanged baseline |
| `AivaV2LanguageBoundaryVerticalTest.fullBookingConversationUsesRealAdapterGatewayKernelToolsAndRendererInAllStyles` | Still failing; unchanged baseline |
| `AivaV2LanguageBoundaryVerticalTest.rescheduleConversationUsesRealServiceToolsAndStructuredResponsesInAllStyles` | Still failing; unchanged baseline |
| `AivaV2LanguageBoundaryVerticalTest.serviceSessionKeysIsolatePatientConversationTenantAndPresentation` | Still failing; unchanged baseline |
| `AivaV2LanguageBoundaryVerticalTest.standaloneControlTurnsKeepEstablishedConversationPresentationThroughService` | Still failing; unchanged baseline |
| `AivaV2PocCertificationTest.callToBookNeverInvokesAvailabilityOrBooking` | Still failing; unchanged baseline |
| `AivaV2PocCertificationTest.englishHappyPathMoreSlotsContextQuestionAndDuplicateConfirmation` | Still failing; unchanged baseline |
| `AivaV2PocCertificationTest.hindiAndEnglishConfirmationHaveIdenticalKernelSemantics` | Still failing; unchanged baseline |
| `AivaV2PocCertificationTest.hinglishDateCorrectionRetainsProviderAndInvalidatesSlot` | Still failing; unchanged baseline |
| `AivaV2PocCertificationTest.sameConversationIdentifierCannotReuseDraftAcrossTenantScopes` | Still failing; unchanged baseline |

No new persistent failures remain. The existing date/daypart gateway fast paths and kernel missing-field handling are marked **COMPATIBILITY — PHASE 6 REMOVAL**; no duplicate pagination or availability owner was added. Phase 5 dependencies remain outside this phase.

`git diff --check` passes. Phase 4 changes do not touch protected appointment lifecycle/mutation, booking mutation, authorization/policy, audit/idempotency, stale-slot validation, tenant ownership, or canonical availability services. Phase 5 has not started.
