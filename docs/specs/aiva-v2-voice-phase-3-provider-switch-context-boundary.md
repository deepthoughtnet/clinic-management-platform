# AIVA V2 Voice Phase 3 — Provider Switch and Context Invalidation

**Status:** Phase 3 implementation and validation complete.
**Owner:** AIVA V2 Voice application orchestration in `api-bff`.
**Persistence/migrations/frontend:** None. Pending switch state is transient and remains in the existing call-scoped `SessionProjection.voiceClarificationState`.

## Placement and dependencies

The implementation is limited to the existing Voice adapter and session model in `api-bff`, its existing transactional boundary/kernel canonical-provider adoption path, and Voice adapter/DotVoice tests. No domain, mutation service, authorization service, audit service, availability business service, persistence owner, migration, or frontend dependency is introduced. The adapter may use existing tenant-scoped provider evidence resolution and canonical adoption; it must not bind public discovery evidence directly.

## Contract

A resolved different provider during an active booking creates a bounded pending provider-switch confirmation. The current provider, draft, availability, slot, and booking confirmation remain unchanged until an explicit YES. Pending state binds the tenant, old provider, target provider identity, draft ID/revision, and originating context. The target is re-resolved against tenant-canonical provider evidence on YES and must still identify the same provider. YES uses the existing canonical provider-adoption and preparation path, preserves requested date and daypart/time preference, and invalidates provider-dependent availability, page, slot selection, and confirmation state. It does not create an appointment.

NO clears only the switch clarification and retains the exact originating booking state, including any valid pending final confirmation. YES/NO while a provider-switch clarification is pending takes precedence over generic booking confirmation. A same-provider request is a continuation. Ambiguous identity is clarified against Phase 1's stored tenant-canonical candidates before a switch warning is presented.

Provider-dependent invalidation is centralized as a pure session/draft transformation. It preserves tenant, patient/call identity, presentation mode, date, and independent time preference; it clears old provider binding and all provider-specific availability, pagination, selected slot, and confirmation capability. At confirmation time the requested date/daypart are carried into canonical preparation for the new provider.

### Pending switch lifecycle

`VoiceClarificationState` is the sole pending-switch owner. Its `PendingProviderSwitch` binds tenant, current and target canonical provider IDs, canonical target display name, originating draft ID/revision/context, requested date/time preference, availability ID/page offset, selected slot reference, and confirmation reference. It stores no public-discovery capability. On YES the binding is checked against the live session and the target is resolved again through tenant-scoped canonical provider evidence. A changed or stale target is not adopted.

If the target identity is ambiguous, provider identity clarification completes first. Only after one canonical identity is resolved does the separate switch warning begin. Generic YES with multiple identity candidates does not select one. A same-provider reaffirmation does not open a switch confirmation. While switch confirmation is pending, YES/NO has precedence over generic booking confirmation and other Voice controls. A different provider request may replace the pending target only after canonical resolution; other answers leave the switch pending and re-prompt.

### Preserve and invalidate matrix

| Transition | Preserved | Invalidated |
| --- | --- | --- |
| Request / wait | Entire current booking, provider, availability, page, selection, confirmation, plus pending target | Nothing |
| NO | Exact current draft, provider, date, daypart/time preference, availability/page, selected slot, confirmation | Pending switch only |
| YES | Tenant, patient/call identity, presentation mode, requested date, independent daypart/time preference | Old provider binding, old availability, page, slot selection, old confirmation and preparation capability |

Date and an independent requested daypart/time preference are kept and loaded for the new provider. If `exactTime` is merely the stored start time of a selected slot, it is cleared on switch; otherwise it is retained as a user preference. This prevents a prior provider's chosen slot from being auto-selected for the new provider. The old conversation draft is marked `ABANDONED` in the session and retained as `suspendedDraft`; this is not appointment cancellation. A fresh draft continues through canonical provider adoption and ordinary preparation. Switching never creates or confirms an appointment.

### AI and transition effects

AI can contribute provider evidence only through the existing bounded/canonical path. It cannot open or confirm a switch, clear the active provider, abandon a draft, or bind a target directly. Explicit switch YES is deterministic: it revalidates the target, invokes canonical adoption, invalidates provider-dependent state, and may load new-provider availability. NO performs no provider or availability calls. Neither path invokes booking mutation. `AIVA_V2_PROVIDER_SWITCH` traces record request, confirmation, decline, IDs, source context, and date-preservation status without provider names or other PHI.

## Compatibility and limitations

All active Voice provider changes must pass switch confirmation when a different provider is already bound. The adapter owns the persisted switch lifecycle; the kernel's active-provider replacement guard is a lower-level safety boundary. Kernel provider adoption remains the canonical write path for booking drafts; gateway decisions are proposals only. Legacy non-Voice and non-active-booking provider resolution paths are `COMPATIBILITY — PHASE 6 REMOVAL`; they remain available only outside an active Voice booking. No draft schema or audit migration is added; the old conversation draft is marked `ABANDONED` in session with structured `PROVIDER_SWITCH` trace reason, not represented as cancellation of an appointment.

## Validation

Coverage includes pending YES/NO precedence, state binding, stale binding rejection, tenant-canonical target revalidation, identity ambiguity before switching, same-provider continuation, switch from slot and confirmation contexts, date/daypart preservation, zero booking mutation, and real DotVoice transcript flows.

Focused command:

```text
mvn -pl backend/api/api-bff -am test '-Dtest=AivaV2VoiceDotVoiceInboundPathTest,AivaV2VoiceConversationAdapterTest,AivaV2TransactionalKernelTest,AivaV2VoiceBookingExecutionTest,AivaLanguageAdapterTest,CommercialCatalogArchitectureTest,CommercialPlatformArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Result: 189 tests, 0 failures, 0 errors, 0 skipped. Suites: DotVoice 30/30, Voice adapter 74/74, kernel 41/41, authorized booking 5/5, language 32/32, architecture 7/7.

The same authoritative Phase 0/1/2 selector was run:

```text
mvn -pl backend/api/api-bff -am test '-Dtest=AivaLanguageAdapterTest,AivaResponseRendererTest,AivaSpokenDateFormatterTest,AivaTransactionCapabilityGateTest,AiAivaV2ConversationDecisionGatewayTest,AivaV2*Test,VoiceBookingExecutionServiceTest,VoiceReadOnlyAivaService*Test,VoiceTransactionPolicyResolverTest,CommunicationTestServiceTest,CommunicationTestWiringContextTest,CommunicationTestControllerTest,DotVoiceInboundListenerLifecycleTest,PatientPortalServiceTest,AppointmentServiceTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Initial run: 537 covered, 506 passed, 12 assertions failed, 2 Testcontainers startup errors, 17 skipped. Retried integration classes with access to the existing Docker daemon (no Docker rebuild/restart): availability boundary 1/1 and transactional reliability 9/9 passed. Effective result: 545 covered, 516 passed, the same 12 baseline assertions failed, 0 errors, 17 skipped. No new persistent failures.

The 12 original failure identities remain unchanged:

| Original Phase 0/1/2 failure | Phase 3 status |
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

`git diff --check` passes. The Phase 3 implementation does not modify protected appointment, booking mutation, authorization, audit/idempotency, stale-slot, tenant-ownership, or availability business services.
