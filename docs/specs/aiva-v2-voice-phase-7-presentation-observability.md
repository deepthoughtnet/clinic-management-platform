# AIVA V2 Voice Phase 7 — Presentation and Observability

**Status:** Phase 7 complete after Phase 7B stabilized date-sensitive test fixtures. The unchanged authoritative broad selector retains only the 12 accepted pre-Phase-7 assertion failures.
**Owner:** `api-bff` Voice application presentation boundary.
**Domain, persistence, migration, frontend:** None.

## Before response-ownership map

| Response class | Current owner(s) | Target owner | Safe scope |
|---|---|---|---|
| Provider/date/daypart clarification | Authorized adapter string helpers; generic path uses `AivaResponseRenderer` | Typed Voice response intent and intent renderer for authorized DotVoice | Voice adapter only |
| Slot page / exhaustion | Adapter, façade availability rendering, and kernel response categories | Voice intent renderer for authorized booking | Keep Web/alternate renderer |
| Provider-switch warning | Adapter helpers | Voice intent renderer | Authorized Voice |
| Confirmation / ambiguity / decline | Adapter helpers; generic kernel and `AivaResponseRenderer` | Voice intent renderer | Authorized Voice |
| Stale / failure / unknown status | Adapter localized helpers; façade/kernel generic responses | Voice intent renderer with distinct safe categories | Authorized Voice |
| Booking success / repeat | Adapter helpers | Voice intent renderer | Authorized Voice |
| Read-only / discovery | `VoiceReadOnlyAivaService.render` | Existing façade read-only renderer with session presentation mode | Keep separate business path |
| Web / alternate Voice | `AivaV2ConversationService` and `AivaResponseRenderer` render structured kernel outcomes | Existing typed shared renderer | Compatibility; no global rewrite |
| Inbound TTS | `CommunicationTestService.playInboundAudio` calls `ReminderVoiceRenderer` | Same TTS owner, supplied with canonical session presentation locale | Locale handoff only |

There is no `AivaV2ResponseRenderer` class. `AivaResponseRenderer` owns structured response rendering for the generic conversation service. Authorized DotVoice currently receives adapter text directly. The inbound TTS boundary currently takes language from `InboundVoiceResult.language`, which can lag the Voice session's persisted `presentationMode`.

## Placement and dependency plan

Add a small typed response-intent model and renderer under the existing AIVA V2 application package. Adapt the authorized Voice adapter to return intent plus display facts while preserving its current validated action/outcome decisions. Carry presentation mode/locale through `TurnResult` to `CommunicationTestService`, and pass that locale to the existing `ReminderVoiceRenderer`. Keep `AivaResponseRenderer`, Web, alternate Voice, TTS provider order, and all business/domain services intact. Add focused tests in `api-bff` for intent rendering, presentation persistence/TTS locale, inbound response traces, and artifact redaction. No new persistence or session fields are planned; `SessionProjection.presentationMode` remains source of truth.

## Response intent contract

The Voice renderer will receive a typed intent, canonical display values only, and the persisted `VoicePresentationMode`. Supported types include provider/date/daypart clarification, slot choices/exhaustion, provider-switch confirmation, booking confirmation/success/stale/failure/status-unknown, completed-booking repeat, and compatibility text. It must not decide a workflow action, resolve an entity, or call a business service. Canonical candidate lists are supplied by the adapter; the renderer never invents choices. Compatibility text remains for branches not migrated in this phase.

## Current execution trace

Authorized inbound: `CommunicationTestService` → `VoiceReadOnlyAivaService.continueTurn` → `AivaV2VoiceConversationAdapter.prepareAuthorizedVoiceBookingTurn` → adapter result text → `CommunicationTestService.playInboundAudio` → `ReminderVoiceRenderer.render(text, language)` → selected TTS provider → media stream. Read-only turns use `VoiceReadOnlyAivaService.render`. Generic Web/alternate Voice uses `AivaV2ConversationService` → kernel structured `MessageResponse` → `AivaResponseRenderer`.

## Validation record

Accepted Phase 6 baseline: 572 run, 560 passed, 12 known assertion failures, 0 errors, 0 skipped. The Phase 7 implementation does not touch appointment mutation, authorization, tenant ownership, provider canonicalization, availability semantics, or stale-slot validation.

## Implemented response ownership

`AivaV2VoiceConversationAdapter` now maps authorized Voice outcomes to `AivaV2VoiceResponseRenderer.Intent`, supplies session-canonical display data, and owns final authorized booking wording. The renderer is pure: it only formats the validated type, persisted presentation mode, canonical provider/candidate data, date/time, displayed slots, and safe booking reference. It cannot choose an action or call a tool. The prior completed-booking summary helper was removed; repeat and success use the same typed renderer.

The renderer supports `PROVIDER_CLARIFICATION`, `DATE_CLARIFICATION`, `DAYPART_CLARIFICATION`, `SLOT_CHOICES`, `SLOT_EXHAUSTED`, `PROVIDER_SWITCH_CONFIRMATION`, `BOOKING_CONFIRMATION`, `BOOKING_SUCCESS`, `BOOKING_STALE`, `BOOKING_FAILED`, `BOOKING_STATUS_UNKNOWN`, `BOOKING_DECLINED`, `COMPLETED_BOOKING_REPEAT`, and compatibility text. It formats no more than three currently displayed slots or clarification candidates. It strips a bounded leading `Dr`, `Dr.`, `Doctor`, or `Doc` from display values before adding the spoken honorific once.

`SessionProjection.presentationMode` is the source of truth. `VoiceReadOnlyAivaService.presentationModeName/presentationLanguage` reads the existing session mode, defaulting to the call language only before a session mode exists. `CommunicationTestService` passes that locale to the existing `ReminderVoiceRenderer`: English uses `en-IN`; Hindi and Hinglish use the existing Hindi-capable `hi-IN` path. Provider selection and priority inside the TTS renderer are unchanged. Normal read-only responses remain owned by the façade and generic Web/alternate-connector responses remain owned by `AivaResponseRenderer`; they are compatibility paths rather than duplicate authorized booking renderers.

### Presentation-language stickiness

The initial Voice turn infers a presentation mode only when the session has no stored mode. After that, ordinary utterances—including short English acknowledgements/courtesy phrases in a Hindi or Hinglish call—retain `SessionProjection.presentationMode`; detected utterance language is not recalculated as a candidate switch. The mode changes only when deterministic explicit-switch wording is recognized (for example, “English please”, “Please speak in English”, “हिंदी में बोलिए”, or “अब English में बात करें”). Courtesy close uses the retained mode for its response and TTS locale. `ttsLanguage` remains `en-IN` for English and `hi-IN` for Hindi/Hinglish; TTS provider selection and implementation are unchanged.

Hindi/Hinglish output stays concise and asks one primary question. It uses familiar terms such as appointment, doctor, slot, morning/evening, and confirm. Slot pages include only currently displayed choices, in order, with spoken times. Exhaustion explicitly says there are no more slots for the current date and offers another date without implying a reload. Confirmation and success include canonical provider/date/time. Stale slot, generic failure, and unknown status use distinct wording; unknown status never claims success or promises asynchronous follow-up.

## Observability and privacy

The adapter emits one `VOICE_CONVERSATION_DECISION` trace with context, action, source, operation, AI invocation, and result. `CommunicationTestService` emits one `VOICE_RESPONSE_RENDERED` trace with hashed conversation identity, turn correlation, context, response intent, decision action, persisted presentation mode, TTS locale, response length, and provider/date/slot presence flags. These traces do not include transcript, rendered text, patient phone, patient name, clinical detail, provider name, booking reference, or raw session serialization. The post-commit session-finalization error trace records only presence flags for execution and appointment correlation identifiers.

Response diagnostics use safe categories already supported by the turn result: `VOICE_PROVIDER_AMBIGUOUS`, `VOICE_CONFIRMATION_BINDING_INVALID`, `VOICE_RECOGNITION_AMBIGUOUS`, `VOICE_BOOKING_STALE`, `VOICE_BOOKING_FAILED`, `VOICE_BOOKING_STATUS_UNKNOWN`, `VOICE_RECONCILIATION_SUCCESS`, and `VOICE_CONTEXT_PROPOSAL_REJECTED`. No telemetry platform or persisted field was added.

## Compatibility and known limits

- Adapter branches that have not been migrated to typed intent rendering retain their validated compatibility response text. Read-only/global discovery remains separate and is not moved into the booking renderer.
- Web and `VoiceOrchestratorService.processAivaV2FinalTranscript` / `AivaV2VoiceTurnConnector` retain their shared `AivaV2ConversationService` and `AivaResponseRenderer` response path. Phase 7 changes only the locale handoff for actual DotVoice inbound turns.
- `CommunicationTestService`'s greeting/legacy no-session path continues to use inbound call language because no persisted conversation mode exists yet.
- English-versus-Hindi TTS locale is verified at the renderer input boundary; acoustic output is outside automated test scope. Hinglish intentionally uses the existing Hindi-capable TTS locale.
- The legacy compatibility text branches and façade read-only response strings remain candidates for a later narrowly scoped cleanup; no business routing was moved into presentation code.

## Focused test evidence

The focused run passed: `AivaV2VoiceDotVoiceInboundPathTest` (51), `AivaV2VoiceConversationAdapterTest` (74), `AivaLanguageAdapterTest` (32), `AivaV2VoiceResponseRendererTest` (5), `ReminderVoiceRendererTest` (3), and `CommunicationTestServiceTest` (30): 195 tests, 0 failures, 0 errors, 0 skipped. DotVoice coverage checks persisted HINDI/ENGLISH mode and verifies `hi-IN`/`en-IN` are passed to the TTS renderer after an explicit mode change. It also exercises localized clarification, slot, confirmation, success, stale, and completed-repeat paths through inbound handling. Renderer tests assert essential intent content and reject internal IDs/operation names.

## Regression and compatibility evidence

The unchanged authoritative selector completed with 578 tests, 566 passed, 12 accepted assertion failures, 0 errors, and 0 skipped. Compared with the accepted Phase 6 baseline of 572 tests, this includes the Phase 7 renderer coverage and two deterministic boundary tests. The same 12 accepted failure identities listed below remain; no new persistent failures remain.

Phase 7B classified and corrected three date-sensitive test fixtures without changing production semantics:

| Failure | Classification | Root cause and correction |
|---|---|---|
| `AivaV2VoiceConversationAdapterTest.tomorrowAvailabilityUsesSelectedDoctorAndClarifiesWhenNoDoctorIsSelected` | TEST_TIMEZONE_DEPENDENCY | Production interprets relative dates in `AivaExecutionContext.timezone()` (Asia/Kolkata in this test). The expected date used JVM-default `LocalDate.now()`. The test now derives expected tomorrow from the same explicit booking-context zone. The mismatch was reproduced under `America/Los_Angeles` (expected 2026-10-26, actual 2026-10-27 on 2026-10-26 UTC / 2026-10-25 Los Angeles / 2026-10-26 Kolkata). |
| `PatientPortalServiceTest.dashboardSkipsPastAppointmentsWhenPickingNextVisit` | TEST_MIDNIGHT_WRAPAROUND | Subtracting two hours from `LocalTime.now(Asia/Kolkata)` could wrap to 22:xx while retaining today's date. The fixture now derives both date and time from `ZonedDateTime.now(Asia/Kolkata).minusHours(2)`, making the appointment unambiguously past. |
| `AivaV2AvailabilityServiceBoundaryIntegrationTest.voiceAvailabilityUsesRealTenantScopedPortalAndAppointmentServices` | TEST_TIMEZONE_DEPENDENCY / date-sensitive fixture | Its fixed date (2026-10-26) became the current date during this work, so morning availability correctly excluded three already elapsed slots. The integration fixture now uses 2027-10-26, keeping the seeded schedule safely in the future. |

The production date sources remain intentionally scoped: Voice normalization uses the execution context's timezone, and Patient Portal upcoming filtering uses the tenant/clinic zone from `ClinicTimeZoneResolver` through `AppointmentTimingRules`. Neither path was changed or globally pinned to Asia/Kolkata. `AivaV2TemporalNormalizerTest` now fixes instants around Kolkata midnight and verifies “tomorrow” before and after that boundary, plus the corresponding UTC date. `AppointmentTimingRulesTest` fixes 00:05 Asia/Kolkata and verifies previous-day, later-same-day, before-slot, and after-slot behavior.

The corrected affected tests plus the new boundary tests passed under both `-Duser.timezone=UTC` and `-Duser.timezone=Asia/Kolkata` (4/4 each). The focused Phase 7 regression selection passed 278/278, including DotVoice 51/51, adapter 74/74, language 32/32, Patient Portal 42/42, kernel 42/42, alternate connector 2/2, shared renderer 12/12, response renderer 5/5, and architecture wiring/redaction checks 3/3. Testcontainers-backed availability boundary passed 1/1 and transactional reliability passed 9/9 with Docker access; Docker was not rebuilt or restarted. Production behavior and protected appointment/mutation/security boundaries were unchanged.

Known baseline failure identities were unchanged:

1. `AiAivaV2ConversationDecisionGatewayTest.activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty`
2. `AivaV2ActiveBookingFollowUpRegressionTest.pendingProviderFieldTreatsShortDoctorPhrasesAsAuthoritativeResolverCandidates`
3. `AivaV2ActiveBookingFollowUpRegressionTest.providerResolutionAdvancesWorkflowWithoutLosingRequestedDate`
4. `AivaV2LanguageBoundaryVerticalTest.fullBookingConversationUsesRealAdapterGatewayKernelToolsAndRendererInAllStyles`
5. `AivaV2LanguageBoundaryVerticalTest.rescheduleConversationUsesRealServiceToolsAndStructuredResponsesInAllStyles`
6. `AivaV2LanguageBoundaryVerticalTest.serviceSessionKeysIsolatePatientConversationTenantAndPresentation`
7. `AivaV2LanguageBoundaryVerticalTest.standaloneControlTurnsKeepEstablishedConversationPresentationThroughService`
8. `AivaV2PocCertificationTest.callToBookNeverInvokesAvailabilityOrBooking`
9. `AivaV2PocCertificationTest.englishHappyPathMoreSlotsContextQuestionAndDuplicateConfirmation`
10. `AivaV2PocCertificationTest.hindiAndEnglishConfirmationHaveIdenticalKernelSemantics`
11. `AivaV2PocCertificationTest.hinglishDateCorrectionRetainsProviderAndInvalidatesSlot`
12. `AivaV2PocCertificationTest.sameConversationIdentifierCannotReuseDraftAcrossTenantScopes`

No accepted baseline failure changed, and no Phase 7 response-renderer test failed. The initial Phase 7 focused validation passed 195 tests; the expanded Phase 7B focused run passed 278 tests. The corrected broad selector has no new failures. `git diff --check` passed. Phase 8 work has not started.
