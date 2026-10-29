# AIVA V2 Voice physical-call dialogue stabilization

Status: approved for implementation from the physical DotVoice certification findings.

## Scope and ownership

The owning bounded context is authorized Voice conversation orchestration in
`api-bff`. The communication adapter remains the sole contextual interpretation
owner; the existing kernel/tools and tenant-scoped read-only gateway retain
operation and data access ownership. No persistence, migration, appointment
domain, frontend, shared Web, or alternate Voice changes are planned.

## Proposed file placement

- `AivaV2VoiceConversationAdapter`: contextual generic availability routing,
  lookup-vs-booking guard, and language-sticky closing.
- `AivaV2DisplayedSlotSelectionResolver`: bounded English/Hindi/Hinglish
  current-page selection recognition only.
- `AivaV2VoiceConfirmationContext`: affirmative phrases only while its existing
  prepared, bound confirmation predicate is active.
- `HealthcareAiReadOnlyToolGateway`: tenant-scoped provider availability
  filtering for generic date-based doctor discovery, using existing
  `AppointmentService` reads.
- `VoiceReadOnlyAivaService`: patient-facing provider result wording and
  effective transaction diagnostics only.
- Existing API BFF Voice adapter/inbound and gateway tests: certification of
  state, action, data-access, and no-mutation behavior.

Allowed dependencies are existing API BFF orchestration and existing domain
application service interfaces. Protected booking execution, canonical stale
slot validation, tenant isolation, authorization, idempotency, and mutation
services remain unchanged.

## Behavioral contract

Current displayed canonical slots alone may satisfy a slot selection. More-slot
pagination retains the same availability snapshot and advances its current page.
Confirmation language is interpreted as affirmative only when the bound
`AWAIT_CONFIRMATION` context is valid. Existing-appointment and sensitive-data
requests stay outside new-booking execution. Generic doctor availability is a
read-only tenant-scoped provider discovery and must retain the requested date.
Conversation closing follows persisted presentation mode, not STT locale.
Diagnostics distinguish discovery, new-booking transaction, and deferred
unsupported transaction, while separating configured legacy OTP values from
the effective booking authorization used.

## Validation plan

Add/update DotVoice inbound, adapter, language, gateway, policy/booking-context,
idempotency, tenant isolation, and unsupported-action assertions. Run focused
tests first, then the broad AIVA V2 selector and `git diff --check`. No Docker
rebuild or runtime configuration changes are part of this work.

## Physical-call defects addressed

- A slot-like utterance could also be mistaken for provider evidence before
  the displayed page resolver ran. The Voice adapter now resolves a recognized
  current-page slot before provider recovery, while an explicit different
  provider request still retains provider-switch precedence.
- `SHOW_MORE` advances the retained availability page without fetching. Once a
  slot is selected, the existing canonical revalidation read still runs before
  confirmation; that read protects stale-slot handling and is not pagination.
- Hindi/Hinglish ordinal, spoken-time, and action-wrapped time phrases resolve
  only to currently displayed canonical slots. Confirmation phrases are
  accepted only inside the existing bound confirmation context.
- A first-person new-booking phrase such as “मेरा appointment book कर दीजिए”
  is no longer classified as an existing-appointment lookup solely because it
  contains ownership language. Explicit lookup/history wording remains blocked
  at the Voice scope boundary.
- Generic date-based doctor availability remains a read-only `FIND_DOCTOR`
  discovery. Its date is retained, and date-based results are filtered to
  tenant-scoped providers with bookable slots. Pediatric aliases normalize to
  the existing Pediatrics specialty. No appointment-history tool is added.
- Closing text follows the active presentation mode. Effective policy
  diagnostics distinguish the restricted new-booking transaction from
  read-only discovery and preserve legacy OTP configuration separately from
  whether a Voice OTP challenge is actually enforced for new booking.

## Evidence

Focused DotVoice inbound certification: 62 tests passed, including full Hindi
and Hinglish morning → pagination → 11:30 → confirmation → duplicate-YES flows,
current-page selection, generic doctor availability, supported pediatric
discovery, and zero OTP service interactions for the tested booking flow.
Displayed-slot resolver: 1/1. Language adapter: 32/32. Voice transaction
diagnostics: 8/8. Adapter: 75/75. Voice booking execution: 6/6. The focused
shared read-only gateway suite has one existing failing Devanagari-name
matching assertion (11/12); the new date-filtered discovery test passes.

The authoritative Phase 8 selector was rerun after the final changes. Its
api-bff reports contain 345 tests: 335 passed, 10 assertion failures, 0 errors,
0 skipped. The ten failure identities remain the accepted set: five
`AivaV2PocCertificationTest`, one `AivaV2ActiveBookingFollowUpRegressionTest`,
and four `AivaV2LanguageBoundaryVerticalTest`; no new failure identity appeared
in that selector. The wider exploratory AIVA V2 selector previously included
additional tests outside that authoritative set; those exposed two
midnight-sensitive absolute-date fixtures and a non-Hindi/Hinglish affirmative
fixture. Those test fixtures were stabilized, and the relevant adapter and
booking-execution suites now pass. No appointment mutation, authorization,
tenant, idempotency, or stale-slot validation service was changed.

## Hindi/Hinglish physical-call quality stabilization

This narrow follow-up retains the same authorized DotVoice path and state owners.
It does not modify booking architecture, tenant/patient authorization, OTP policy,
provider canonicalization, mutation services, idempotency, or canonical stale-slot
revalidation.

The reproduced dialogue defects had distinct causes:

- A read-only pending daypart only recognized normalized morning/evening tokens;
  Sarvam's contextually plausible `Shyam` transcript was not a daypart answer.
  The adapter now maps that exact token to evening only when `VOICE_PENDING_DAYPART`
  is the retained cursor. Provider/date context remains sourced from the same
  retained availability result.
- The displayed-slot resolver covered numeric clock and a few English/Hindi
  phrases, but not compound Hindi/Hinglish time speech. A parsed time that did not
  match the displayed page could continue into generic booking interpretation.
  A pure spoken-time parser now returns candidate LocalTime values; the resolver
  binds only a unique match on the current displayed canonical page. A missing or
  ambiguous match produces a targeted clarification with the page and cursor
  preserved. Selection still passes through the existing canonical stale check.
- Observed `Or slots` / `Other slots, tell me` ASR variants were absent from the
  AWAIT_SLOT contextual control set. They now resolve as SHOW_MORE only in that
  context, using the existing retained snapshot and cursor.
- Courtesy phrases were recognized after some active-booking handling. They now
  close early in both authorized continuation entry points, before any provider,
  availability, or booking operation. The response uses the persisted
  presentation mode.
- Initial presentation mode detection treated every Devanagari utterance as
  Hindi. Mixed Hindi-English vocabulary can now select HINGLISH initially;
  subsequent turns remain sticky to the persisted mode. Safe mode diagnostics
  record before/detected/after/reason without transcript text.
- Appointment slot output previously formatted Hindi times as numeric clocks
  with AM/PM/daypart suffixes. A pure formatter now handles HINDI, HINGLISH, and
  ENGLISH from canonical LocalTime values. Hindi provider speech mappings affect
  output only; canonical provider IDs/names remain unchanged in state and
  authorization.
- Known General Medicine and Pediatrics role aliases now resolve before doctor
  name extraction. Generic date-based doctor availability continues to use the
  existing tenant-scoped FIND_DOCTOR discovery path and returns provider names
  when the result is deterministic.

Focused regressions cover Hindi/Hinglish spoken times against the retained
second page, unmatched-time clarification, contextual Shyam daypart, observed
pagination transcripts, localized spoken-time output, and specialty-alias
precedence. Test evidence for this follow-up is appended after its verification
run.

### Verification result — 2026-10-27

- Focused Maven regression selection: **302 tests / 302 passed / 0 errors / 0
  skipped**. This included DotVoice inbound (65), Voice adapter (76), displayed
  slot resolver (4), language adapter (33), response renderer (6), booking
  execution (6), shared booking execution service (5), transaction policy
  (16), Voice turn connector (2), transactional kernel (42), communication
  service (30), schedule rendering (16), and log redaction (1).
- The Hindi and Hinglish inbound tests exercise the daypart continuation,
  second-page canonical slot selection, observed `Or slots` ASR variants,
  deterministic confirmation, retained date/provider, specialty aliases,
  doctor availability names, courtesy close, and no duplicate confirmation.
- The unchanged Phase 8 selector: **350 tests / 327 passed / 10 assertion
  failures / 0 errors / 13 skipped**. The ten failures are the same recorded
  compatibility/provider-scope/reschedule identities: five
  `AivaV2PocCertificationTest`, one
  `AivaV2ActiveBookingFollowUpRegressionTest`, and four
  `AivaV2LanguageBoundaryVerticalTest`. The 13 skips are Docker-dependent
  distributed-session tests unavailable in this run. No new failure identity
  appeared.
- `git diff --check`: passed. No appointment mutation or execution service,
  tenant/patient authorization, idempotency, canonical provider identity,
  confirmation binding, or stale-slot validation code was changed. Shared Web
  and alternate Voice remain disabled. No Docker build/restart or physical call
  was performed for this code-only stabilization.

## Generic consultation clarification

Generic consultation language is represented as `CONSULTATION_REQUEST`, a
non-mutating intent distinct from `BOOKING` and `LOOKUP`. A new consultation
starts with tenant-scoped doctor/specialty discovery. A generic request with
multiple specialties asks the patient to choose from specialties returned by
that tenant lookup; multiple providers in one specialty ask the patient to
choose from the bounded returned provider set. A single provider is offered
with a read-only prompt to check slots. No provider is guessed and no booking
transaction starts from consultation wording alone.

Pending specialty/provider choice and date/daypart are retained in the existing
`VoiceClarificationState`, `ProviderSearchResult`, and
`AvailabilityResult` session projections. Relative Hindi/Hinglish dates in a
consultation request resolve as future dates. Explicit existing appointment
questions remain at the unsupported patient-data boundary. Provider IDs and
canonical names remain the only identity used for later transactional booking;
localized specialty/provider text is response-only.

The owning context remains authorized Voice orchestration in `api-bff`. No new
store, migration, business service, booking execution path, Web route, or
alternate Voice route is introduced.

## Current-page slot selection and pagination follow-up

Physical-call review found that the retained full `AvailabilityResult` was
available after read-only slot discovery, but the read-only continuation path
did not consult its displayed page before generic intent/provider routing.
That allowed a natural time plus booking phrase to lose the already known date
and return to generic date collection. Pagination on that path also re-entered
`FIND_SLOTS` even though the retained result already contained the complete
canonical candidate list.

The follow-up keeps the two AWAIT_SLOT actions explicit: advance the retained
page, or select one uniquely matching displayed time/ordinal. Page advancement
updates only `displayOffset`; it does not call provider or availability tools.
Selection resolves only within the current page and carries the selected
canonical time plus retained provider/date/daypart into the existing authorized
booking preparation. That preparation still obtains/validates canonical
transactional availability and preserves the existing final confirmation and
stale-slot checks; read-only display data is never accepted as a booking slot
reference. Unmatched or ambiguous selections clarify without changing the page.

The Sarvam speaker mismatch came from two configuration layers: local Compose
defaulted `SARVAM_TTS_SPEAKER` to `anushka`, and `SarvamTextToSpeechProvider`
allowed legacy `AIVA_DEFAULT_VOICE` to override the typed Sarvam speaker when
the new property was absent. The local Compose fallback now matches the
approved `aparna_hi_customer`; the typed Sarvam property takes precedence over
the legacy shared voice setting. An explicit `SARVAM_TTS_SPEAKER` deployment
override remains authoritative.

The focused current-page spoken-time/ordinal, SHOW_MORE offset, booking
confirmation, and Sarvam payload tests passed: **169/169** across the resolver
(6), conversation adapter (77), DotVoice inbound (71), Sarvam provider (8), and
reminder renderer (7). The Phase 8 selector with the added slot resolver and
Sarvam provider coverage reported **372 tests / 349 passed / 10 assertion
failures / 0 errors / 13 skipped**. The ten failures are the same recorded
baseline identities (five POC, four language-boundary, and one active-booking
follow-up); no new failure identity appeared. The 13 existing skips are the
Docker-dependent cases.

The speaker request is covered with a mocked Sarvam REST response and verifies
the effective JSON payload. No live Sarvam request was made, so this confirms
the configured speaker reaches the provider but does not establish that the
external API now returns success for it. The running API was not rebuilt or
restarted. The local `.env.full-docker` value and Compose fallback are set to
`aparna_hi_customer` for the next local start; the production candidate env
file was not changed.

Verification for the consultation clarification change:

- Focused selection: **181 tests passed** (DotVoice inbound 71, conversation
  adapter 76, language adapter 34; zero failures/errors/skips).
- Authoritative Phase 8 selector: **357 tests / 347 passed / 10 assertion
  failures / 0 errors / 13 skipped**. The failures match the accepted baseline
  identities: `AivaV2ActiveBookingFollowUpRegressionTest.providerResolutionAdvancesWorkflowWithoutLosingRequestedDate`;
  four `AivaV2LanguageBoundaryVerticalTest` cases (`fullBookingConversationUsesRealAdapterGatewayKernelToolsAndRendererInAllStyles`,
  `rescheduleConversationUsesRealServiceToolsAndStructuredResponsesInAllStyles`,
  `serviceSessionKeysIsolatePatientConversationTenantAndPresentation`,
  `standaloneControlTurnsKeepEstablishedConversationPresentationThroughService`);
  and five `AivaV2PocCertificationTest` cases (`callToBookNeverInvokesAvailabilityOrBooking`,
  `englishHappyPathMoreSlotsContextQuestionAndDuplicateConfirmation`,
  `hindiAndEnglishConfirmationHaveIdenticalKernelSemantics`,
  `hinglishDateCorrectionRetainsProviderAndInvalidatesSlot`,
  `sameConversationIdentifierCannotReuseDraftAcrossTenantScopes`). No new
  failure identity was introduced. The 13 skips are the selector's existing
  Docker-dependent cases.
- `git diff --check`: passed.
- No booking mutation, authorization, tenant isolation, idempotency,
  confirmation, or stale-slot validation boundary was changed. No Docker
  rebuild/restart or physical telephony test was performed.

## Late Hindi slot selection and Sarvam speaker follow-up

The latest physical transcript, `नौ बजे का बुक कर दो`, exposed a lexical
collision: the Hindi clock word `नौ` was also emitted as doctor evidence (`Dr
Nau`). When slot selection entered booking preparation, that false provider
evidence prevented adoption of the already bound active provider and the
booking draft lost its date. A uniquely resolved time on the retained page now
suppresses only clock-word provider artifacts, then sends the canonical
displayed time and retained date/provider/daypart through normal booking
preparation. The resulting draft still requires the existing explicit final
confirmation. Hindi SHOW_MORE paging remains based on the retained snapshot.

The local Compose speaker fallback and the provider's typed-property
precedence now resolve to `aparna_hi_customer`; an explicit Sarvam-specific
environment override remains highest priority. Hindi/Hinglish and English
provider order and the 16 kHz configured Sarvam sample rate are unchanged.
The Sarvam mock REST test confirms the configured model/speaker/language/pace/
sample-rate payload and successful provider response. No live Sarvam request
was made, so external API acceptance after the speaker correction remains to
be confirmed during local runtime certification.

Latest focused regression run: **233 tests passed** across the displayed-slot
resolver (6), conversation adapter (77), DotVoice inbound (72), booking
execution (6), shared booking execution service (5), Voice response renderer
(6), language adapter (34), shared response renderer (12), Sarvam provider
(8), and reminder renderer (7). This includes the new DotVoice transcript
regression for selecting the displayed 21:00 slot, preserving its date and
provider, requiring confirmation, creating exactly one booking, and replaying
confirmation without a duplicate.

Latest authoritative Phase 8 selector: **380 tests / 357 passed / 10
assertion failures / 0 errors / 13 skipped**. The ten failures are unchanged:
`AivaV2ActiveBookingFollowUpRegressionTest.providerResolutionAdvancesWorkflowWithoutLosingRequestedDate`;
four `AivaV2LanguageBoundaryVerticalTest` cases;
and five `AivaV2PocCertificationTest` cases. No new failure identity appeared.
The 13 skips remain Docker-dependent. `git diff --check` passed. No booking
mutation, authorization, tenant isolation, idempotency, confirmation, or
stale-slot validation boundary changed; Docker was not rebuilt/restarted and
no physical call was made.

## Sarvam Linear16 and playback-recovery follow-up

The Sarvam REST request now explicitly sends `output_audio_codec=linear16`
with the existing model, speaker, language, pace, and configured speech sample
rate. The current certification target remains 16 kHz. The expected JSON
`request_id` / `audios[0]` response is decoded as `audio/L16` (PCM16 mono),
validated before Sarvam is accepted by the renderer, and passed through the
existing `ReminderAudioNormalizer` and DotVoice G.711 μ-law conversion. No MP3
decoder, WAV header synthesis, or alternate DotVoice encoder was added. If
Sarvam returns a visible unsupported audio content type or malformed PCM, the
Sarvam attempt fails before playback selection and the configured fallback
provider can be attempted.

Playback preparation and conversion failures now notify the existing DotVoice
inbound lifecycle boundary. For a non-closing call the inbound handler clears
`botSpeaking`, resets partial capture state, marks the call `LISTENING`, and
updates the inbound status. Closing calls retain their closing/hangup behavior.

Verification: Sarvam provider **9/9**, reminder renderer **8/8**, audio
normalizer **4/4**, communication service **32/32**, inbound playback recovery
handler **1/1**, DotVoice inbound **72/72**, Voice adapter **77/77**, Voice
booking execution **6/6**, booking execution service **5/5**, and DotVoice
media client **11/11** passed. The Phase 8 selector reported **419 tests / 396
passed / 10 assertion failures / 0 errors / 13 skipped**. The same accepted
failure identities remain: one active-booking follow-up, four
language-boundary, and five POC certification assertions. No new failure
identity appeared. ElevenLabs implementation and ordering were not changed;
its existing English selection/regression tests remain green. The selected
Sarvam REST response was mocked; no external Sarvam request or physical call
was performed. Docker was not rebuilt or restarted.
