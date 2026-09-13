# Jeevanam AIVA V2 Architecture Proof and Tool Contract Audit

## Scope

This is a design and runtime audit only. It does not propose an immediate production cutover and does not modify AIVA runtime behavior, voice transport, Provider/Discover rules, entitlement, RBAC, or appointment business rules.

The audit used current source, architecture documents, existing specifications, and the latest available `clinic-api` runtime logs.

## 1. Architecture Verdict

**RECOMMENDED WITH CHANGES**

The proposed V2 direction is substantially better than the current runtime, but two assumptions should change:

1. The conversational model should propose semantic intent and an operation, not own business workflow admissibility.
2. Zero-or-one tool call should be a latency goal, not an absolute rule. A single model decision may require a bounded deterministic sequence of read operations, such as provider resolution followed by availability lookup.

The recommended principle is:

> The model understands the request.  
> A server-side Conversation Kernel decides whether the proposed operation is valid.  
> Domain-owned tools establish and change healthcare truth.

This remains one orchestrator, not multi-agent.

**GO for an isolated text-only V2 POC.**

**NO-GO for production integration until current tool contracts are normalized.**

## 2. Evidence Reviewed

Important source evidence:

- The primary service is 7,471 lines and directly owns interpretation, routing, fallback, state mutation, tool invocation, and response selection: `PatientPortalCareAiService`.
- Raw phrase routing still exists before and after `CanonicalTurn`.
- `TurnInterpreter` independently performs intent, confirmation, selection, alternative, and correction parsing.
- Runtime state exists in multiple mutable and durable representations.

Relevant files:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiTurnInterpreter.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiToolRegistry.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiBusinessLookupService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalConversationStateService.java`
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/careai/persistence/CareAiConversationPersistenceService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalService.java`

## 3. Why Current AIVA Fails Structurally

### 3.1 Too Many Semantic Authorities

A turn currently passes through competing decision points:

```text
Raw message checks
-> planner eligibility
-> Gemini/Groq planner
-> EntityExtractor
-> TurnInterpreter
-> deterministic intent fallback
-> WorkflowRouter
-> applyIntent/applyBookingFacts
-> TopicClassifier
-> booking handlers
-> raw confirmation helpers
-> fallback policy
-> skill
-> further state mutation
```

A correct earlier interpretation does not guarantee a correct final action.

Specific evidence:

- Emergency, confirmation, greeting, new-patient, callback, and handoff semantics are checked before `TurnInterpreter`.
- `WorkflowRouter` still receives raw message text.
- `classifyTopic` runs after canonical semantics have already been applied.
- Booking handlers still receive raw message text.
- Confirmation has both canonical and legacy raw paths.
- Slot resolution can perform more than selection.

### 3.2 CanonicalTurn Is Not Actually the Only Authority

The interpreter has a material merge defect:

```java
plannerDecision == null ? extracted.doctor() : plannerDecision.doctorName()
```

If Gemini returns a planner decision but leaves `doctorName` null, a valid extractor doctor is discarded. Specialty behaves the same way.

By contrast, date and time use first-non-null merging. This inconsistency can selectively lose doctor identity.

### 3.3 State Has Multiple Owners

Current state is distributed across:

- `PatientPortalCareAiService.CareAiState`
- `sessions`
- `voiceSessions`
- `PatientPortalConversationStateService.snapshots`
- persisted CareAI conversation records
- persisted workflow snapshots
- candidate contexts
- slot options and alternate slot collections
- fallback/no-match fields
- confirmation flags
- shadow reducer state

This makes restore, correct, and clear operations ambiguous.

### 3.4 Confirmation Is a Routing State Instead of a Capability

Runtime logs showed `doctor.find` invoked while booking was already at `CONFIRMATION_PENDING`.

A positive confirmation should not enter normal booking routing. It should consume one server-issued, revision-bound confirmation capability.

### 3.5 Tests Prove Components, Not One Runtime Authority

The current corpus heavily mocks `PatientPortalService.doctors()`, slot methods, and other dependencies. Many broad natural-language groups remain exploratory or `SUPPORTED_BUT_INCOMPLETE`.

The tests can therefore verify resolvers, seeded transitions, and mocked skill outputs without proving that the live path preserves the same doctor, criteria, candidate set, and confirmation across all intermediate authorities.

## 4. Exact doctor.find Contract Failure

Runtime evidence showed:

```text
Business lookup resultCount=1
skill outcome=NO_MATCH
```

The exact conversion stage is in `PatientPortalCareAiToolRegistry`:

1. `businessLookupService.findDoctors(...)` returns valid merged doctors.
2. The registry applies a second `locationQuery` filter.
3. If that filter removes all results, the registry emits `NO_MATCH`.

The logs did not include the exact `locationQuery` value, but no other conversion occurs between the logged positive lookup count and registry `NO_MATCH`.

- **Confirmed failing stage:** ToolRegistry post-lookup filtering.
- **Strong inference:** stale or extractor-generated location state supplied the rejecting filter.
- **Not the failing stage:** Care/private and Discover source retrieval.

This contract is broken because a valid authoritative result is reinterpreted by an orchestration-layer filter.

## 5. Current Tool Contract Audit

| Contract | Classification | Findings |
|---|---|---|
| `doctor.find` | **BROKEN** | Positive business results can become `NO_MATCH`; location filtering is duplicated; Care-private doctors are converted to public DTOs; source and capability provenance are lost. |
| `availability.check` | **NEEDS_NORMALIZATION** | Server-side authorization exists, but input has several competing provider identifiers; multiple slots are represented as `MULTIPLE_MATCHES`; time-window filtering occurs elsewhere; no criteria revision or guaranteed slot identity. |
| `appointment.book` | **UNSAFE_FOR_V2 as a direct model tool** | Domain booking validation is strong, but the tool accepts caller-provided `bookingMode`, multiple identity fields, date/time instead of a prepared confirmation token, and a raw `confirmed` boolean. |
| `PatientPortalService.doctors()` | **READY behind a normalized adapter** | Correctly requires authenticated patient access and returns active current-tenant Care doctors. |
| `PublicCatalogFacade.listDoctors()` | **READY behind a normalized adapter** | Correct public source, subject to Discover publication rules. |
| `PatientPortalService.doctorAvailability()` | **READY behind a normalized contract** | Validates patient access/provider/date and already supports future availability. |
| `PatientPortalService.bookAppointment()` | **NEEDS_TRANSACTIONAL_WRAPPER** | Revalidates doctor and slot and creates through appointment services, but current idempotency is read-then-write rather than command reservation. |
| Current `SkillResult<T>` | **NEEDS_NORMALIZATION** | Generic message/value/count fields mix transport presentation with domain outcomes and omit criteria identity, provenance, revision, retry safety, and stable result references. |

### Authorization and Visibility

Current doctor retrieval includes both:

- authenticated Care doctors through `PatientPortalService.doctors()`;
- published Discover doctors through `PublicCatalogFacade.listDoctors()`.

However, private doctors are mapped into `PublicDoctorSummaryResponse` with:

- missing clinic/tenant identity;
- no explicit `CARE_PRIVATE` provenance;
- hardcoded `ONLINE_BOOKING`;
- hardcoded `canBookOnline=true`.

That output cannot safely represent the actual authorization and capability distinctions required by V2.

### Booking Safety

`PatientPortalService.bookAppointment()` correctly:

- authenticates the patient;
- resolves provider identity server-side;
- enforces tenant/public booking rules;
- revalidates the selected slot;
- creates through appointment-domain services.

But idempotency currently performs:

```text
find cached response
-> execute appointment creation
-> store response
```

Two concurrent requests can both miss the cache before either stores the response. The database unique key prevents duplicate idempotency records, not necessarily duplicate appointments.

This persistence also currently lives in `api-bff`, contrary to the repository's current module rules.

## 6. Independent Recommended Architecture

The recommended design is an **AIVA Transactional Conversation Kernel**.

```text
Voice/Text Channel
-> Normalized Turn Envelope
   conversationId, turnId, transcript, STT confidence, channel
-> Context Loader
   active draft, latest bounded result, recent meaningful turns
-> Provider-Neutral Decision Gateway
   tiny deterministic fast path OR one structured LLM call
-> Validated ConversationDecision
-> Transactional Conversation Kernel
   validates operation against session/draft/revision/policy
-> Domain-Owned Booking Tools
-> Structured Tool Result
-> Session Projection + Audit Event
-> Deterministic Transactional Response
   optional LLM wording only for non-transactional conversation
-> TTS for voice
```

### Critical Difference From the Proposed V2

The model does not own the workflow.

The model owns:

- natural-language interpretation;
- dialog act;
- semantic changes;
- proposed operation;
- conversational wording.

The Conversation Kernel owns:

- whether the operation is valid now;
- which draft/result it applies to;
- whether a read sequence may proceed;
- whether confirmation can execute;
- stale-result rejection;
- business-tool invocation.

This avoids recreating a large FSM while preventing the model from becoming a transactional authority.

## 7. State Ownership

| Concept | Sole Authoritative Owner |
|---|---|
| Conversational context | AI-domain conversation-session projection |
| Active/suspended topic | Conversation Kernel session projection |
| Booking draft | Appointment-domain booking draft service |
| Doctor identity | Authorized provider-resolution tool result stored as a provider handle |
| Specialty/date/time preference | Revisioned booking draft |
| Latest doctor candidates | Bounded candidate result store, referenced by `candidateSetId` |
| Latest availability | Availability result store, referenced by `availabilityRequestId` |
| Slot identity | Availability result plus authoritative slot handle |
| Confirmation | Appointment-domain one-time confirmation record |
| Pending write | Appointment-domain command ledger |
| Retries | Command ledger/reconciliation policy |
| Provider fallback | AI-domain Decision Gateway |
| Idempotency | Appointment-domain durable command execution |
| Conversation audit | AI-domain immutable interaction audit |
| Appointment audit | Appointment-domain transaction audit |
| Voice session lifecycle | Existing voice gateway/runtime |
| Authentication/RBAC | Existing server-side security context |

## 8. Provider-Neutral Model Decision

```json
{
  "schemaVersion": "aiva.conversation-decision.v1",
  "dialogAct": "CHANGE_BOOKING",
  "operation": "GET_AVAILABILITY",
  "bookingPatch": {
    "doctorText": null,
    "specialtyText": null,
    "dateExpression": "next Monday",
    "timeWindow": "afternoon"
  },
  "selection": null,
  "confirmation": "NONE",
  "topicAction": "CONTINUE",
  "responseLanguage": "hi-IN",
  "confidence": 0.94
}
```

The model must never emit patient IDs, tenant IDs, doctor database IDs, clinic database IDs, slot truth, confirmation token contents, authorization results, or booking results.

It may return a candidate reference only when that reference was included in its bounded context. The kernel validates it against the current result set.

### Recommended LLM Pattern

- One structured semantic call for a normal natural turn.
- Tiny deterministic fast path for exact controls, exact dates/times, ordinal selection, and unambiguous confirmation.
- No planner plus interpreter plus classifier.
- No multi-agent.
- No direct native provider-specific tool contract.
- Optional second LLM call only for non-transactional prose where deterministic composition is insufficient.

## 9. Minimum V2 Tool Set

Six tools are enough for the booking slice.

| Tool | Responsibility | Type | Confirmation | Idempotency |
|---|---|---|---|---|
| `resolve_booking_provider` | Search/select an authorized Care or published Discover provider and update provider criteria atomically | Read plus draft mutation | No | Expected draft revision |
| `get_booking_availability` | Apply date/time preference, invalidate stale slot state, and retrieve authoritative availability | Read plus draft mutation | No | Request ID and draft revision |
| `select_booking_slot` | Select only from the latest valid availability result | Draft mutation | No | Expected draft revision |
| `prepare_booking` | Revalidate draft/provider/slot and issue a one-time confirmation capability | Read/prepare | Returns confirmation request | Idempotent by draft revision |
| `confirm_booking` | Consume confirmation capability and create the appointment | Write | Yes | Mandatory durable command ID |
| `abandon_booking` | Revoke pending confirmation and abandon draft | Draft mutation | No | Expected draft revision |

Draft creation is an internal appointment application operation when the first booking command arrives. It does not need to be exposed as a model tool.

### Bounded Read Chaining

For "Book Akshu tomorrow," one LLM decision may result in:

```text
resolve_booking_provider
-> if uniquely resolved, get_booking_availability
```

This is preferable to forcing an unnecessary extra conversational turn or creating a broad do-everything tool. No write chain is allowed.

## 10. Provider Identity Contract

```text
ProviderSearchResult
{
  resultId
  status: RESOLVED | AMBIGUOUS | NOT_FOUND | SUGGESTION
  criteriaFingerprint
  candidates[]
  resolvedProvider?
  generatedAt
  expiresAt
}
```

Each candidate contains:

```text
candidateHandle
displayName
specialty
clinicDisplayName
source: CARE_PRIVATE | DISCOVER_PUBLIC
capabilities:
  careAuthorized
  publiclyPublished
  onlineBooking
  liveAvailability
  callToBook
  publicPhoneAvailable
  publicProfileAvailable
```

Rules:

- Zero matches: `NOT_FOUND`.
- One authoritative exact/alias match: `RESOLVED`.
- Multiple plausible matches: `AMBIGUOUS`.
- Near/fuzzy-only match: `SUGGESTION`.
- A suggestion never updates `doctorId`.
- Candidate handles are opaque and scoped to patient, tenant, result, and expiry.
- Explicit correction clears the previous provider before evaluating replacement.
- Unresolved replacement leaves the draft without a selected provider rather than silently restoring the old one.

Therefore, "Akshay Kumar" cannot silently become "Akshu Kumar."

## 11. BookingDraft

```text
BookingDraft
{
  draftId
  patientSubjectId
  tenantScope

  selectedProvider {
    providerHandle
    doctorId
    clinicId
    tenantId
    source
    displayNameSnapshot
    capabilities
  }

  specialtyFilter
  preferredDate
  preferredTimeWindow
  exactTime

  latestAvailabilityRequestId
  selectedSlotId

  status
  revision
  expiresAt
  confirmedAppointmentId
}
```

Recommended statuses:

```text
COLLECTING
READY_FOR_CONFIRMATION
SUBMITTING
RECONCILIATION_REQUIRED
CONFIRMED
ABANDONED
EXPIRED
```

The draft belongs in `appointment-domain`, not `api-bff`, because it represents preparation for an appointment transaction.

The draft must not contain raw messages, prior assistant wording, `lastQuestionKey`, full candidate lists, multiple slot arrays, topic classifications, previous no-match prompts, model confidence, or voice state.

## 12. AvailabilityResult

```text
AvailabilityResult
{
  requestId
  draftId
  draftRevision
  criteriaFingerprint

  providerHandle
  doctorId
  clinicId
  date
  timeWindow
  timezone

  slots[] {
    slotId
    startsAt
    endsAt
    displayTime
    availabilityVersion
    expiresAt
  }

  cursor
  hasMore
  generatedAt
  expiresAt
}
```

Behavior:

- "Second one" selects slot 2 from this exact result.
- "4:30" resolves only against this result.
- "Show more" uses its cursor.
- "Anything later?" requests a new page/filter based on this result.
- Date, doctor, clinic, service, or time-window change increments draft revision and invalidates the result.
- A slot cannot be selected if result revision differs from current draft revision.
- Old result sets are retained only for audit, never as active selection context.

## 13. Confirmation Token

```text
BookingConfirmation
{
  confirmationToken
  draftId
  draftRevision
  providerHandle
  doctorId
  clinicId
  availabilityRequestId
  availabilityVersion
  slotId
  startsAt
  expiresAt
  commandId
}
```

The token should be opaque. The server stores or cryptographically binds its claims.

`confirm_booking` must:

1. Authenticate the patient.
2. Resolve the token server-side.
3. Verify patient ownership and tenant scope.
4. Verify token status and expiry.
5. Verify exact draft revision.
6. Verify provider capability.
7. Revalidate slot availability.
8. Reserve `commandId` atomically.
9. Execute at most once.
10. Persist the authoritative result.
11. Return booked, unavailable, or reconciliation-required.

The model does not need to see the token. When it returns an unambiguous `CONFIRM` operation, the kernel injects the current server-held token.

This eliminates confirmation loops:

```text
valid pending token + positive confirmation
-> confirm_booking
```

No doctor lookup, date parsing, availability lookup, or workflow routing occurs.

## 14. Correction Semantics

Corrections use typed patch semantics:

```text
UNCHANGED
SET(value)
CLEAR
```

| User turn | Draft operation |
|---|---|
| "No, tomorrow instead." | Revoke token; set date; clear selected slot and availability reference; retain provider |
| "Actually Monday." | Same date correction behavior |
| "Another doctor." | Revoke token; clear provider, slot, availability; search provider |
| "Keep Akshu." | Keep current authoritative provider handle; no global name search |
| "4:30." | Select only from latest valid availability result |
| "Show more slots." | Continue latest availability request using cursor |
| "Anything later?" | New availability query constrained after latest displayed time |
| "What date are these for?" | Answer from current `AvailabilityResult`; no mutation |
| "Let me ask something else." | Suspend active draft |
| "Continue my booking." | Resume draft if authorized and not expired |
| "Forget it." | Abandon draft and revoke token |

If a correction arrives while a booking write is executing, the kernel must reconcile that write first. It cannot blindly cancel or submit another booking.

## 15. Topic Switching

Use only:

```text
activeDraftId
suspendedDraftId
latestToolResultRef
```

Policy:

- Side question with no booking change: keep draft active.
- Explicit ask-something-else action: suspend draft.
- Continue booking: restore the suspended draft after ownership and expiry validation.
- Forget booking: abandon draft.
- Context questions about doctor/date/time: answer directly from draft/result.
- A new unrelated transactional intent may suspend the booking draft rather than destroy it.

A large `TopicClassifier` is unnecessary.

## 16. Multilingual Design

English, Hindi, and Hinglish share one semantic schema and one tool/runtime path.

Requirements:

- Preserve original and normalized doctor query text.
- Transliteration may produce suggestions but never authoritative identity.
- Doctor IDs only come from provider tools.
- Relative dates use clinic timezone and one injected clock.
- Ambiguous numeric dates require clarification.
- Confirmation maps to `POSITIVE`, `NEGATIVE`, or `AMBIGUOUS`.
- Low-confidence STT confirmation must not execute a write.
- Language changes do not change draft or conversation identity.
- No language-specific booking workflows.

## 17. Model Provider Strategy

Current orchestration supports Gemini-to-Groq fallback. The current check establishes structural JSON completeness, not semantic equivalence.

Both providers must return the same versioned `ConversationDecision` schema.

Provider conformance must verify:

- schema validity;
- allowed operation values;
- no invented IDs;
- equivalent patch semantics;
- confirmation polarity;
- candidate selection;
- English/Hindi/Hinglish equivalence.

Fallback policy:

- Read-only request: fallback allowed if output validates.
- Correction: fallback allowed, but revision and identity rules remain server-enforced.
- Exact confirmation: deterministic fast path where possible.
- Ambiguous confirmation: clarification, not provider guessing.
- Transactional model output never directly performs a write.
- Invalid or low-confidence fallback output: clarification.
- Provider, model, schema version, confidence, and `fallbackUsed` are audited.

Automatic Groq fallback is not yet proven transactionally safe merely because both providers produce JSON.

## 18. Healthcare Safety Boundary

### Model May

- Interpret natural language.
- Identify conversational goals.
- Propose a booking operation.
- Extract semantic provider/date/time candidates.
- Identify corrections and selections.
- Ask clarification.
- Compose non-authoritative wording.
- Choose response language.

### Model Must Not Own

- Patient identity.
- Doctor identity.
- Clinic or tenant identity.
- RBAC.
- Entitlement.
- Care-private visibility.
- Discover publication.
- CALL_TO_BOOK capability.
- Slot availability.
- Appointment ownership.
- Booking/cancel/reschedule outcome.
- Idempotency.
- Confirmation capability.
- Audit truth.
- Clinical safety truth.

## 19. Failure Handling

| Failure | Required behavior |
|---|---|
| Gemini 429 | Try certified fallback provider; validate schema/policy before mutation |
| Groq semantic divergence | Reject invalid operation; clarify high-risk ambiguity; audit provider |
| Low-confidence STT | Clarify names/confirmations; no write |
| Doctor search zero | `NOT_FOUND`; no stale doctor retained |
| Doctor search many | `AMBIGUOUS`; bounded candidates |
| Fuzzy doctor | `SUGGESTION`; no identity mutation |
| CALL_TO_BOOK doctor | Explain capability; no live availability or booking |
| Availability empty | Successful no-availability result, not dependency failure |
| Availability timeout | `TIMEOUT`; preserve validated criteria |
| Slot disappears | Revoke confirmation; clear slot; return conflict/current options |
| Booking timeout | `RECONCILIATION_REQUIRED`; no blind retry |
| Duplicate confirmation | Return same consumed command outcome |
| WebSocket replay | Duplicate turn/command IDs safely ignored or replayed |
| Reconnect | Reload session, draft, result, and command state |
| Late stale result | Revision mismatch; discard |
| Provider outage | `TEMPORARILY_UNAVAILABLE`, not `NOT_FOUND` |

## 20. LLM Budget

Ordinary transactional turn:

```text
0 or 1 semantic LLM call
-> 0 to 2 bounded read tools
-> deterministic response
```

Confirmation turn:

```text
deterministic confirmation fast path
-> confirm_booking
-> deterministic response
```

A second LLM call is unnecessary for provider choices, slot lists, no availability, confirmation prompts, successful booking, expired slots, authorization failures, or CALL_TO_BOOK responses.

## 21. Latency Architecture

Current observed live timing:

| Stage | Observed |
|---|---:|
| STT | 1,063 ms |
| CareAI | 1,861 ms |
| TTS | 1,398 ms |
| Total | 4,324 ms |

Recommended targets:

| Stage | Target |
|---|---:|
| Speech end to STT final | 300-700 ms |
| Context loading | Under 100 ms, overlapped with STT finalization |
| Semantic decision | 300-700 ms |
| Provider/availability read | 100-800 ms |
| Deterministic response | Under 20 ms |
| TTS first audio | 300-600 ms |
| Simple end-to-first-audio | Approximately 1.2-2.5 seconds |

Useful optimizations:

- Load active draft/latest result during STT finalization.
- Use deterministic confirmation and candidate-selection fast paths.
- Avoid a response-composition LLM call.
- Preserve existing progress events for slow tools.
- Stream STT/TTS only where currently reliable.
- Never execute partial streamed model tool arguments.
- Avoid speculative business calls before semantic validation.

## 22. Reuse, Freeze, and Retire

| Component | Recommendation |
|---|---|
| Voice transport, VAD, STT, TTS | **KEEP** |
| Voice progress and barge-in | **KEEP** |
| Authentication, RBAC, entitlement | **KEEP** |
| Provider/Discover boundaries | **KEEP** |
| Appointment services | **KEEP** |
| AI provider router/audit | **KEEP**, add conformance gate |
| Skill execution lifecycle | **REUSE** |
| Stale-result suppression | **REUSE**, strengthen restart durability |
| Existing idempotency concept | **REUSE CONCEPT**, relocate and make atomic |
| `PatientPortalService` booking primitives | **REUSE BEHIND TOOLS** |
| Public/private doctor lookups | **REUSE BEHIND NORMALIZED CONTRACT** |
| `DoctorResolver` | **INVESTIGATE/REUSE strict matching only** |
| `SpecialtyResolver` | **REUSE** |
| `SelectionResolver` | **REUSE against one current result only** |
| Current `ToolRegistry` | **REUSE lifecycle skeleton, replace booking contracts** |
| `PatientPortalCareAiService` | **TEMPORARILY FREEZE** |
| Current `CanonicalTurn` | **FREEZE for legacy** |
| Current reducer/action shadow path | **FREEZE as comparison evidence** |
| EntityExtractor as authority | **RETIRE FROM V2** |
| WorkflowRouter | **RETIRE FROM V2 booking** |
| TopicClassifier | **RETIRE FROM V2** |
| Current fallback registry | **RETIRE FROM V2 booking** |
| Previous-response-driven routing | **RETIRE** |
| Duplicate candidate/slot collections | **RETIRE** |
| Raw confirmation helpers | **RETIRE FROM V2** |
| `lastQuestionKey` as action authority | **RETIRE** |

## 23. What to Stop Doing Immediately

1. Stop adding raw phrase conditions to the legacy service.
2. Stop adding new fallback-state fields.
3. Stop expanding duplicate doctor or slot representations.
4. Stop treating metadata-only skill definitions as complete contracts.
5. Stop accepting green mocked tests as proof of runtime coherence.
6. Stop automatically trusting provider fallback semantics without conformance evidence.
7. Stop moving new persistence into `api-bff`.

## 24. Architecture Comparison

| Dimension | Original AIVA | CanonicalTurn + Reducer | Recommended Kernel + Draft |
|---|---|---|---|
| Complexity | Very high | High during dual-path migration | Moderate |
| Natural conversation | Low/medium | Medium | High |
| Action reliability | Low in live integration | Potentially high, not proven | High by draft/token invariants |
| State consistency | Low | Better but duplicated | One owner per concept |
| Latency | High | Medium/high | Low/medium |
| Testability | Component-heavy/mocked | Good pure tests | Strong contracts/conversation tests |
| Multilingual | Phrase-heavy | Better | Provider-neutral semantics |
| Fallback safety | Low | Medium | High |
| Healthcare safety | Domain checks exist; routing fragile | Better action constraints | Explicit model/domain boundary |
| Maintainability | Poor | Medium | High |
| Migration risk | Already accumulated | Medium | Medium with isolated POC |
| Extensibility | Low | Medium | High |

## 25. First Text-Only POC

### Placement

- **Owning domain:** appointment-domain for draft, token, and write-command semantics.
- **AI orchestration:** ai-domain for provider-neutral decision schema and provider conformance.
- **API module:** `api-bff` only as an isolated text harness/inbound adapter.
- **Persistence:** in-memory POC repositories only.
- **Database migration:** none.
- **Frontend:** none.
- **Production writes:** mocked or restricted test-tenant adapter only.

### Components

- `ConversationDecision` schema.
- Gemini and Groq decision adapters.
- `AivaConversationKernel`.
- In-memory revisioned `BookingDraftStore`.
- In-memory candidate/availability result store.
- Six normalized mock/domain-adapter tools.
- Deterministic transactional response composer.
- Safe trace collector.
- Multilingual provider-conformance corpus.

### Trace Per Turn

```text
conversationId
turnId
provider
fallbackUsed
modelDecision
validatedOperation
toolCall
safe toolInput
toolResult
draftBefore
draftAfter
latestResultBefore/After
assistantResponseCategory
latencies
```

A credible isolated proof should take roughly **7-10 working days for one senior engineer**, including fixtures, multilingual provider conformance, and deterministic trace analysis.

## 26. Acceptance Test Matrix

| # | Scenario | Required proof |
|---:|---|---|
| 1 | "Book Akshu tomorrow." | Akshu resolved authoritatively; availability loaded |
| 2 | "What slots are available?" | Latest valid availability reused/refreshed once |
| 3 | "Anything later?" | Current-result cursor/time constraint |
| 4 | "4:30 PM." | Selection only from latest valid result |
| 5 | "Actually Monday instead." | Token/result invalidated; provider retained |
| 6 | "Show me another doctor." | Provider and slot cleared; new search |
| 7 | "No, keep Akshu." | Known handle restored, not fuzzy global match |
| 8 | "What date are these slots for?" | Context answer; no tool call |
| 9 | "Yes, book it." | One `confirm_booking`; no provider/availability call |
| 10 | Duplicate "Yes" or replay | Exactly one appointment mutation |
| 11 | Akshay when only Akshu exists | `NOT_FOUND` or `SUGGESTION`; never replacement |
| 12 | English happy path | Correct revisions and one write |
| 13 | Hindi happy path | Same semantic/tool sequence |
| 14 | Hinglish correction path | Same revision/token behavior |
| 15 | Gemini 429 to Groq | Same validated operation/draft mutation |
| 16 | Late old availability result | Old result stale; no mutation |
| 17 | CALL_TO_BOOK provider | No availability or booking write |
| 18 | Booking response timeout | Reconciliation; no duplicate write |

The POC passes only if:

- no stale doctor reappears;
- no Akshay-to-Akshu silent match occurs;
- no stale slot survives criteria changes;
- no positive lookup becomes `NO_MATCH`;
- confirmation executes once;
- no provider/availability lookup occurs after valid confirmation;
- multilingual semantics are equivalent;
- provider fallback cannot change business truth.

## 27. Migration Strategy

### Phase 0: Contract Normalization

- Normalize provider identity and capability output.
- Remove post-authoritative filtering from tool contracts.
- Define availability result identity and revision.
- Define confirmation capability.
- Add atomic appointment-domain command idempotency.
- Add Gemini/Groq conformance tests.

### Phase 1: Isolated Text POC

- In-memory drafts.
- Test-tenant or mocked writes.
- No production path changes.

### Phase 2: Shadow Text Comparison

- Legacy and V2 consume the same input.
- V2 performs no writes.
- Compare provider identity, date, slot, operation, and response category.

### Phase 3: UAT-Only Feature Flag

- UAT identities only.
- Booking only.
- Independent V2 state.
- Immediate rollback to legacy.
- No mutable state sharing between engines.

### Phase 4: Existing Voice Connection

- Feed final STT text into the proven V2 text entrypoint.
- Reuse progress, TTS, barge-in, and voice state.
- No voice-specific business workflow.

### Phase 5: Expansion

- Cancellation.
- Rescheduling.
- Appointment status.
- Discovery.
- Other capabilities only after separate contract proofs.

## 28. Top Five Risks

1. **Tool normalization changes visibility accidentally.** Care-private and Discover-public sources must remain distinct and fully tested.
2. **Provider fallback semantic drift.** Gemini and Groq may produce valid JSON with different operations.
3. **Non-atomic current idempotency.** A timeout/replay race can still reach appointment creation twice.
4. **Legacy/V2 state contamination.** Shadow and UAT paths must not share mutable booking fields or execute writes twice.
5. **Overloading the Conversation Kernel.** It must remain a policy/admissibility coordinator, not become another oversized workflow service.

## 29. Final Architect Decision

1. **Architecture chosen:** provider-neutral semantic decision gateway plus a deterministic Transactional Conversation Kernel, appointment-domain revisioned BookingDraft, latest-result handles, and one-time confirmation capabilities.
2. **Why:** it provides natural interpretation without allowing models or fallback routers to own healthcare truth.
3. **Stop immediately:** phrase routing, fallback-state expansion, duplicate candidate lists, repeated doctor resolution, previous-response-driven decisions, and unverified transactional provider failover.
4. **Build first:** normalized provider identity, availability result, confirmation-token, and atomic idempotency contracts, followed by an isolated text POC.
5. **Reuse:** voice stack, authentication, RBAC, entitlement, Provider/Discover services, appointment services, lifecycle protection, progress events, stale suppression, audit, and strict resolver behavior.
6. **Retire from V2:** current booking router, topic classifier, legacy fallback system, raw semantic helpers, duplicate mutable booking state, and `lastQuestionKey` authority.
7. **Top three risks:** provider semantic variance, atomic idempotency, and legacy/V2 coexistence.
8. **Evidence that would change this recommendation:** inability to issue stable slot/confirmation handles, unacceptable multilingual provider conformance, or an isolated POC showing repeated ambiguity despite bounded context.
9. **Proceed with AIVA V2 POC:** **YES**.
10. **Proceed with production integration now:** **NO**.

Production integration remains blocked until Phase 0 tool normalization and atomic confirmation/idempotency guarantees are complete.
