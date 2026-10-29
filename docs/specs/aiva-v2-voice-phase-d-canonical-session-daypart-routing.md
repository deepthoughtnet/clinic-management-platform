# AIVA V2 Voice Phase D: Canonical Session Routing and Daypart Narrowing

## Scope

LOCAL Voice orchestration consolidation. Preserve Phase A contextual date,
Phase B canonical slot selection, Phase C final confirmation, and existing
read-only doctor/schedule/availability behavior. No new mutation boundary,
authentication behavior, transaction type, or session store is introduced.

## Ownership and placement

- Owning bounded context: AIVA V2 Voice conversation/application orchestration.
- API module: `backend/api/api-bff`.
- Persistence owner: existing `AivaV2SessionStore` / `SessionProjection`;
  no schema change or migration.
- Availability owner: existing AIVA V2 booking tools and canonical
  appointment availability service.
- Frontend: none.
- Allowed dependencies: Voice adapter to existing session, language,
  transaction kernel, booking tools and availability interfaces; the Voice
  service may route the turn to the existing adapter path.

## Planned files

- `AivaV2VoiceConversationAdapter`: bounded deterministic routing using
  current turn facts and persisted workflow, active doctor adoption, and
  contextual daypart continuation.
- `AivaV2TransactionalKernel`: retain canonical availability and slot refs,
  mark a pending daypart continuation when multiple dayparts have slots, and
  return direct slot choices when only one daypart is available.
- `VoiceReadOnlyAivaService`: route contextual booking intent with a canonical
  active doctor into the existing authorized booking path before generic
  read-only handling.
- Focused adapter, kernel, and inbound-path tests for state transitions and
  daypart narrowing.

## State ownership

`SessionProjection.activeVoiceDoctor` is the canonical conversational doctor
(canonical ID plus display name). `BookingDraft.selectedProvider` is the
transaction's provider binding and adopts the active doctor when booking
starts, without another lookup. Current-turn doctor evidence and resolution
remain ephemeral. `AvailabilityResult` remains the canonical offered-slot
collection; its existing cursor carries pending daypart selection.

## Routing and availability behavior

One bounded router selects among existing read-only, booking start/update,
slot selection, confirmation/decline, clarification, and conversation-end
paths. It runs deterministic contextual continuation before generic decision
fallback and does not replace the transactional kernel.

Booking availability without exact time, time range, or explicit daypart is
split by the existing canonical boundaries: morning before 12:00, afternoon
12:00 through 16:59, evening from 17:00. Multiple populated dayparts prompt
for only those dayparts and persist the pending state in the availability
cursor. One populated daypart offers canonical slots directly. A daypart
answer applies an existing availability constraint and retrieves/selects
canonical slots through the existing booking path. Exact time and time range
take precedence over daypart narrowing.

## Safety and validation

Mutation, trust policy, authentication, and operation isolation remain
unchanged. Selection still resolves against canonical offered slot
references. No migration is expected. Validate focused state-transition and
inbound routing tests, Phase A/B/C regressions, availability/daypart and
session tests, broader AIVA V2 tests, Maven package, and `git diff --check`.
