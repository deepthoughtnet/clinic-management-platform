# AIVA V2 Voice State Alignment and Flow Tracing

## Scope

This local-only batch aligns booking continuation with the existing call-scoped `SessionProjection` and adds structured, privacy-safe stage diagnostics. It does not add slot selection, booking mutation, appointment lookup, cancellation, or rescheduling behavior.

## Ownership and placement

- Owning bounded context: AIVA V2 Voice application orchestration.
- API module: `backend/api/api-bff`.
- Persistence: existing `AivaV2SessionStore` serialization of `SessionProjection`; no new store or schema.
- Migration owner: none; this batch adds no persisted fields requiring a migration.
- Frontend: none.
- Allowed dependencies: existing language adapters, Voice execution context, decision gateway, transactional kernel/boundary, and session store.

## Canonical state

`SessionProjection` is the sole persisted source for active Voice doctor, active booking draft, pending confirmation, provider/availability results, auth/guest state, and recovery state. Overall workflow is derived from this projection; no parallel workflow flag is introduced. `BookingDraft.selectedProvider` is the booking’s canonical provider identity, while `activeVoiceDoctor` is the session-level provider context. During booking continuation, a valid active doctor is adopted into an unbound draft without clearing that doctor.

The Voice session key remains `VOICE|<tenantId>|<sessionId>`. The inbound adapter sets `sessionId` and `providerCallId` to the same call identifier; end-of-call cleanup uses that same tuple.

### State ownership inventory

| State | Owner and property | Read/write path | Persistence and lifetime | Duplicate representation |
|---|---|---|---|---|
| Session projection | `AivaV2Models.SessionProjection` | `AivaV2VoiceConversationAdapter`; `AivaV2TransactionalKernel`; `AiAivaV2ConversationDecisionGateway` | `AivaV2SessionStore`; Redis when available, in-memory fallback; call session TTL and explicit close | Session payload is the canonical shared object |
| Active doctor | `SessionProjection.activeVoiceDoctor` | Read-only adapter provider resolution writes; adapter/provider-continuation reads; booking bridge adopts into draft | Same projection/call key | Also denormalized into `BookingDraft.selectedProvider` after binding |
| Read-only date/daypart/paging context | `SessionProjection.latestAvailabilityResult` including date, daypart/window, cursor, page offset | `AivaV2VoiceConversationAdapter` read-only tool result and follow-up resolution | Same projection/call key | Date may also appear in booking draft once a booking workflow owns it |
| Booking draft | `SessionProjection.activeDraft` → `BookingDraft` | Decision gateway reads; adapter creates/patches; transactional kernel owns state transitions | Same projection/call key | Provider/date are intentionally mirrored with session context for transaction preparation |
| Workflow | Derived `SessionProjection.activeWorkflow()` | Gateway and kernel diagnostics derive it from active/pending workflow state | Not stored independently | No parallel workflow flag; draft/pending state is authoritative |
| Provider suggestion | `SessionProjection.latestProviderResult` and its candidate/origin turn | Provider resolution writes; selection/confirmation fast paths read; kernel stores booking provider after accepted selection | Same projection/call key; bounded by session/result expiry | Candidate may be copied into draft only after accepted resolution |
| Pending continuation | Existing availability cursor and pending confirmation/reschedule/cancellation fields | Read-only adapter and gateway consume; kernel updates transaction pending state | Same projection/call key | Gateway label is derived from the specific pending state |
| Voice authorization | `SessionProjection.voiceAuthenticationState` | `VoiceReadOnlyAivaService` and adapter read/update; auth challenge/verify flow writes | Same projection/call key; grant TTL plus session end | No second auth store; OTP challenge persistence is separate challenge authority, not conversational workflow state |
| Guest capability/registration | `SessionProjection.voiceGuestRegistrationState` | `VoiceReadOnlyAivaService` registration flow writes; booking bridge reads | Same projection/call key and capability expiry | Patient ID also appears as booking subject after registration; identity is not authentication |
| Clarification/recovery | `SessionProjection.voiceClarificationState` | Voice adapter recovery logic reads/writes | Same projection/call key | No task persistence in this flow state |
| In-memory adapter/session wrapper | `AivaV2SessionStore` fallback map, turn reservations/history/presentation | `update`, `updateTurn`, `find`, and end/cleanup methods | Process-local when Redis unavailable; otherwise Redis projection plus reservation/history data | It is the store implementation, not a second domain session model |
| Communication call state | `CommunicationTestService` inbound call/result maps and `CommunicationInteraction` | DotVoice handler and inbound service | Call lifetime/diagnostics and interaction persistence | Transport transcript/turn records are diagnostics, not canonical semantic state |
| Decision state | `ConversationDecision` | Gateway creates per-turn value; kernel consumes it | Turn-local, not persisted | Semantic output is not workflow state |

### Exact key check

`VoiceReadOnlyAivaService.executionContext` sets both `sessionId` and `providerCallId` from the inbound call ID. `AivaV2VoiceConversationAdapter.sessionKey` and its cleanup method use `VOICE|tenantId|sessionId` and `VOICE|tenantId|providerCallId`, respectively. The decision gateway and kernel receive the projection loaded under that key; they do not independently derive another key. Booking preparation is invoked inside the adapter’s update of the same key. This batch adds a log assertion of `sessionId == providerCallId` at the Voice boundary.

## Continuation behavior

When a booking draft is collecting a required date and has a resolved provider, a deterministic current-turn date is resolved before generic decision classification. The adapter uses the existing `AivaV2TemporalNormalizer` with the trusted Voice timezone, with the existing locale temporal normalizer as fallback for supported language forms. The decision gateway recognizes date facts only in this missing-date draft state and routes them as deterministic `UPDATE_BOOKING`; ambiguous/invalid date facts reach the kernel for clarification rather than generic LLM operation classification. This context-specific disambiguation does not alter general-language normalization. A date-only continuation also discards a language adapter's false provider reference for filler such as `के लिए`, preserving the existing canonical draft provider.

Provider precedence remains explicit canonical provider, pending confirmed suggestion, existing draft provider, active Voice doctor, then unresolved. This batch aligns the existing draft with the active Voice doctor before decision/kernel processing.

## Observability

Flow logs use tenant ID, hashed call/conversation correlation, turn ID, and turn number. They never include caller transcript, phone, patient identifiers, OTP, or secrets. Semantic fields are structured and bounded to canonical provider IDs/names, operation, dialog act, workflow, draft status/revision, date source, authorization source, and execution decision.

## Validation

Add sequential tests through the final-STT inbound service path for a booking request followed by `कल`, and read-only doctor resolution followed by a booking request and `कल`. Verify provider identity and draft revision continuity while the kernel reaches date-present preparation/availability behavior without entering slot-selection or mutation logic.
