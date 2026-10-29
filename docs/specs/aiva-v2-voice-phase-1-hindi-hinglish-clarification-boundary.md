# AIVA V2 Voice Phase 1 — Hindi/Hinglish Interpretation and Clarification Boundary

**Status:** Phase 1 COMPLETE — implementation and verification gates passed. Bounded to the existing AIVA V2 Voice adapter.  
**Owner:** AIVA V2 Voice application orchestration in `api-bff`.  
**Persistence/migrations/frontend:** None. Session recovery state remains in the existing call-scoped `SessionProjection`.

## Recognition contract

`VoiceRecognitionResult` carries action, quality, source, optional ambiguity/candidates, and whether clarification is recommended. `EXACT` means deterministic/context-valid; `STRONG` means context resolves a bounded STT variant and is safe for non-destructive continuation; `AMBIGUOUS` means no workflow-changing action may run; `NO_MATCH` means preserve state and ask a contextual question. No probability score is implied.

Raw STT remains evidence. Provider query evidence strips leading `Dr`, `Dr.`, `Doctor`, and `Doc`, and bounded wrappers such as `Talk` and `mujhe`; a trailing `doctor` is removed from query evidence for phrases such as `Amit doctor`. Stored canonical/display names strip leading honorifics for matching only and retain identity-bearing suffixes such as `Demo Doctor` and `Doc UAT Automation Doctor`. Display labels come only from the canonical provider record and may render `Dr.` separately. Normalization neither creates nor adopts a provider.

## Context precedence

Precedence in the Voice adapter is: (1) pending clarification answer, (2) displayed-choice and current-context controls, (3) deterministic current-turn facts, (4) provider/date/daypart evidence validated in the current context and through canonical provider resolution, (5) bounded gateway AI proposal, and (6) contextual clarification. Pending clarification is first converted to an authoritative `VoiceRecognitionResult` before any global confirmation/provider parsing. A pending SHOW_MORE yes becomes SHOW_MORE; no becomes NEGATE; any other answer remains CLARIFY/NO_MATCH. A yes is consumed before ordinary confirmation handling; a no clears only that clarification. Current displayed-slot context can interpret bounded `plot`/`प्लॉट` and pagination variants as `SHOW_MORE`, then suppresses unrelated provider evidence. Provider resolution results are reflected back into `VoiceRecognitionResult`; unresolved and ambiguous provider evidence is not adopted.

The result type is the authoritative Phase 1 interpretation outcome for controls and provider-resolution results at the existing Voice adapter boundary. The older gateway and kernel still receive `NormalizedUserTurn` and retain compatibility decisions for cancellation/rescheduling and non-Voice web chat; they are not yet migrated to this Voice-specific result because doing so would alter unrelated product paths and exceed Phase 1 scope.

This does not introduce a new global FSM or a permanent decision layer. The recognition contract is called at the current adapter boundary; the existing gateway and session projection remain compatibility paths for controls not yet migrated. Duplicate adapter/gateway rules are scheduled for later consolidation, not treated as new authority.

## Clarification and state preservation

Clarification is contextual and offers a small relevant choice. A slot clarification persists `pendingAction=SHOW_MORE`, bounded answer options, attempt count, and `resumeContext` in existing `VoiceClarificationState`; a following yes means show more and is consumed before booking confirmation. No clears only the clarification, retaining the original availability page and booking draft. The existing bounded retry counter changes the prompt on repeated ambiguity and then uses the existing safe fallback. Clarification must preserve provider, date, daypart, availability identity, selected slot, draft revision, and confirmation bindings. SHOW_MORE alone advances the display offset while retaining availability identity.

If a distinct canonical provider is recognized while a provider is already active in a booking draft, Phase 1 reports `PROVIDER_SWITCH_CONFIRMATION_REQUIRED` and retains the active provider/draft. If provider evidence is ambiguous, identity clarification happens first. Full provider-switch lifecycle is deferred to Phase 3.

## Provider-identity clarification lifecycle

The authorized preparation path uses the existing `VoiceClarificationState` with kind `PROVIDER_IDENTITY` and pending action `PROVIDER_IDENTITY`. It stores a bounded list of tenant-canonical identities containing only provider ID, tenant ID, and canonical display name. Candidate capability is not persisted from public discovery evidence. When the prior read-only path left a generic ambiguous-doctor clarification, authorized preparation re-resolves the original provider evidence using the tenant-scoped canonical resolver and replaces the generic state before responding.

On the following turn, pending provider identity outranks global yes/no and provider extraction. Name fragments and ordinals are matched only against the stored list (maximum three); multiple matches remain ambiguous, and unmatched text remains `NO_MATCH`. Multi-candidate `yes` cannot select a doctor. A single-candidate yes/no prompt accepts yes and safely clears on no. A selected ID is re-resolved and checked against the stored ID and tenant before use. If that canonical identity is no longer usable, the clarification is cleared and the caller is asked to name the doctor again. Session expiry remains the existing call-scoped session TTL; no independent timer is added.

With no active provider, a selected candidate is sent through the existing deterministic booking decision and canonical kernel adoption path. With an active different provider, identity selection only emits `PROVIDER_SWITCH_CONFIRMATION_REQUIRED` / `PROVIDER_SWITCH_DEFERRED_TO_PHASE_3`; it does not replace the active provider or invalidate the draft. AI does not run for a pending candidate answer, and a deterministic selected provider becomes the bounded canonical provider patch rather than an AI proposal.

## AI boundary

AI runs only after deterministic gateway paths. Its output remains proposal data. Phase 1 explicitly rejects deterministic or AI `START_BOOKING` proposals over an active draft; the rejected AI proposal becomes `UNKNOWN` for contextual recovery. Transactional mutation, authorization, idempotency, stale-slot validation, and tenant ownership boundaries are unchanged.

## Presentation

New provider-switch and slot-continuation clarification uses the existing turn presentation mode (Hindi, Hinglish, or English); no parallel language state is introduced.

## Verification and compatibility paths

- Current-context controls are recognized through the `VoiceRecognitionResult` boundary before provider lookup or AI fallback. `SHOW_MORE`, `SELECT_SLOT`, confirmation polarity, date/daypart facts, provider evidence, canonical provider outcomes, ambiguity, and no-match are represented without confidence numbers.
- Hindi/Hinglish controls covered in the language and inbound suites include pagination (`और स्लॉट बताओ/दिखाओ`, `और प्लॉट बताओ`, `अगला स्लॉट बताओ`, `नेक्स्ट स्लॉट बताओ`, `next slot`, `next slot batao`, `aur dikhao`, `more slots`), yes/no, today/tomorrow/day-after-tomorrow, morning/evening, and first/second/third ordinals. Pagination meaning is applied only with a retained slot page; `plot` is not globally rewritten.
- DotVoice tests run through `CommunicationTestService` and `VoiceReadOnlyAivaService`, using the final STT transcript and retained call session. The 22-case suite verifies page continuation, pending SHOW_MORE yes/no, ambiguous Hindi/Hinglish recovery, canonical provider identity follow-up by name and yes, active-provider ambiguity followed by Phase 3 handoff, provider-like STT corruption, and deterministic controls without booking mutation or unnecessary semantic AI invocation.
- The ambiguous-provider vertical fixture must supply tenant-canonical provider records. Public discovery summaries alone are not executable provider candidates in the authenticated tenant resolver. This repairs test evidence only; production canonical adoption remains unchanged.

## Compatibility — Phase 6 removal

- `EnglishLanguageAdapter` / `HindiLanguageAdapter` still produce `NormalizedUserTurn` controls used by non-Voice web conversation and compatibility paths.
- `AiAivaV2ConversationDecisionGateway` retains its own fast paths and bounded AI extraction for web conversation. Voice uses deterministic adapter recognition first and invokes the gateway only when unresolved supported operations require it.
- `AivaV2TransactionalKernel` remains the workflow authority downstream of the adapter; this phase does not replace its operation model or refactor duplicated non-Voice rules.

## Validation and known limitations

Focused coverage is in `AivaLanguageAdapterTest`, `AivaV2VoiceConversationAdapterTest`, `AivaV2VoiceDotVoiceInboundPathTest`, `AivaV2LanguageBoundaryVerticalTest`, and `AiAivaV2ConversationDecisionGatewayTest`. The DotVoice suite enters via `CommunicationTestService` and final STT transcript processing; this does not assess acoustic STT accuracy. Its controls assert the deterministic adapter/gateway may receive normalized turns while `AiOrchestrationService` is not invoked. The `aur dikhao` assertion therefore guards semantic AI, not the deterministic gateway call.

The focused architecture selectors `CommercialCatalogArchitectureTest` (3/3) and `CommercialPlatformArchitectureTest` (4/4) pass. These are the repository's available architecture suites under the selector; no new Voice-to-domain dependency, persistence owner, or mutation path was introduced. Protected appointment lifecycle and security/mutation files were not edited for Phase 1.

Authoritative Phase 0 effective baseline: 527 tests, 497 passed, 13 assertion failures, 0 errors after retry, 17 skipped. Phase 1C used the same selector. Its sandboxed run reported 528 tests, 12 assertion failures, 2 Testcontainers socket-access errors, 17 skipped. Retrying the two integration classes with Docker socket access (no Docker rebuild/restart) passed 1/1 and 9/9. Effective Phase 1C totals are 536 tests, 507 passed, 12 persistent assertion failures, 0 errors, 17 skipped. Relative to Phase 1B, the two added tests are the provider-identity session serialization assertion and the added DotVoice case; no persistent failure was added. The original `answerEnvelopeIsAccepted` assertion passes because matching evidence and canonical display identity are separated. The prior provider-ambiguity regression passes with tenant-canonical fixtures. The explicit-provider-change authorized test expectation requires the Phase 3 handoff and retained Doctor A draft, as required by the Phase 1 safety contract.

| Original Phase 0 failure | Current status | Phase 1C explanation |
|---|---|---|
| `AiAivaV2ConversationDecisionGatewayTest.activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty` | Still fails | Existing deterministic gateway short-circuit differs from the test's expectation of an AI invocation; unchanged. |
| `AiAivaV2ConversationDecisionGatewayTest.answerEnvelopeIsAccepted` | Passes | Matching evidence omits honorific; canonical display identity is separate. Assertion now reflects the provider evidence contract. |
| `AivaV2ActiveBookingFollowUpRegressionTest.pendingProviderFieldTreatsShortDoctorPhrasesAsAuthoritativeResolverCandidates` | Still fails | Legacy gateway extraction retains `Talk` in the pending provider phrase; compatibility path remains for later consolidation. |
| `AivaV2ActiveBookingFollowUpRegressionTest.providerResolutionAdvancesWorkflowWithoutLosingRequestedDate` | Still fails | Existing test fixture does not meet the tenant UUID/canonical provider resolver contract. |
| `AivaV2LanguageBoundaryVerticalTest.fullBookingConversationUsesRealAdapterGatewayToolsAndRendererInAllStyles` | Still fails | Existing short-name fixture does not resolve canonically. |
| `AivaV2LanguageBoundaryVerticalTest.serviceSessionKeysIsolatePatientConversationTenantAndPresentation` | Still fails | Same canonical provider fixture issue blocks the intended isolation assertion. |
| `AivaV2LanguageBoundaryVerticalTest.rescheduleConversationUsesRealServiceToolsAndStructuredResponsesInAllStyles` | Still fails | Existing reschedule pagination assertion; outside Phase 1 booking interpretation. |
| `AivaV2LanguageBoundaryVerticalTest.standaloneControlTurnsKeepEstablishedConversationPresentationThroughService` | Still fails | Existing short-name fixture does not resolve canonically. |
| `AivaV2PocCertificationTest.callToBookNeverInvokesAvailabilityOrBooking` | Still fails | Existing provider fixture does not satisfy canonical adoption. |
| `AivaV2PocCertificationTest.englishHappyPathMoreSlotsContextQuestionAndDuplicateConfirmation` | Still fails | Existing short provider fixture does not resolve canonically. |
| `AivaV2PocCertificationTest.hindiAndEnglishConfirmationHaveIdenticalKernelSemantics` | Still fails | Existing Hindi confirmation semantic assertion; not changed by the Voice pending-clarification scope. |
| `AivaV2PocCertificationTest.hinglishDateCorrectionRetainsProviderAndInvalidatesSlot` | Still fails | Existing fixture never canonically adopts the provider before date correction. |
| `AivaV2PocCertificationTest.sameConversationIdentifierCannotReuseDraftAcrossTenantScopes` | Still fails | Existing provider fixture fails before its intended cross-tenant assertion. |

There are **zero new persistent failing tests** after the contract-aligned provider-switch expectation and tenant-canonical fixture corrections. The ambiguous-provider vertical test passes with tenant-canonical providers, and the authenticated resolver still returns AMBIGUOUS for `Dr R`; public discovery evidence is not upgraded to tenant capability. No stale security fixture was used to weaken canonical adoption.

Known limitations: legacy gateway/provider extraction and kernel decisions remain compatibility paths outside the Phase 1 Voice boundary and are marked **COMPATIBILITY — PHASE 6 REMOVAL** above. Cancellation/rescheduling and web-chat semantics are not migrated. Provider-switch execution remains Phase 3 work; Phase 1 resolves the intended target, emits the handoff, and retains the current draft. Clarification candidates are bounded to three canonical tenant providers. DotVoice tests validate final transcripts and call-session behavior, not acoustic STT accuracy. The 12 listed baseline assertion failures remain unchanged and outside this phase; Testcontainers startup access errors were eliminated by the authorized retry.
