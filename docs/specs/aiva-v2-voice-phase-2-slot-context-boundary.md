# AIVA V2 Voice Phase 2 — Slot Context Boundary

**Status:** Phase 2 complete (validated against the accepted Phase 1 baseline). Bounded to existing AIVA V2 Voice orchestration in `api-bff`.
**Owner:** AIVA V2 Voice application orchestration.
**Persistence/migrations/frontend:** None. `SessionProjection.latestAvailabilityResult` remains the availability and pagination owner.

## Goal

Derive one logical `AWAIT_SLOT` context from existing canonical session state and make the Voice adapter decide one admissible slot-context action before generic gateway interpretation. This is a narrow context boundary, not a complete FSM. It does not change appointment mutation, authorization, canonical stale-slot validation, tenant ownership, or availability business semantics.

## Logical context predicate

`AivaV2VoiceSlotContext.isAwaitSlot(session, now)` is the shared predicate. It requires an open BOOKING session with tenant and patient/call context; a collecting, unexpired draft with a canonical CARE_PRIVATE doctor/clinic/handle and preferred date; no selected slot or pending confirmation; and a non-expired, non-empty `latestAvailabilityResult` matching tenant, provider, clinic/handle, draft revision and date, whose displayed page is in bounds and non-empty. Pending daypart/provider cursors do not qualify. `hasUndisplayedSlots` uses that same snapshot's offset/page size/slot count.

## Actions and precedence

For an AWAIT_SLOT Voice turn, deterministic context recognition runs before global provider extraction or semantic AI and yields one normalized action: select a currently displayed slot, show the next page of the retained snapshot, explicitly search a future date, change date, change provider (existing Phase 3 handoff only), change daypart/time preference, abandon, help/repeat/end, or contextual clarify/no-match. A bounded gateway proposal, if reached, must be one of the allowed actions. Incompatible proposals are rejected and recover contextually without state mutation.

`SHOW_MORE` is pagination of the current `latestAvailabilityResult`; `FIND_NEXT_AVAILABLE_SLOT` remains an explicit future-date search and is admitted by the transactional kernel's preparation-only allow-list. Ambiguous "next" with a valid undisplayed page means `SHOW_MORE`; future-date search requires explicit next-date language. Adapter interpretation is authoritative before generic gateway fallback. Existing gateway fast paths remain **COMPATIBILITY — PHASE 6 REMOVAL** for non-slot and non-Voice callers; the Voice adapter bypasses them for recognized slot actions.

## State ownership and transitions

The existing `SessionProjection.latestAvailabilityResult` owns the availability identity, slots, offset, page size, and `hasMore`. `SHOW_MORE` changes only the displayed offset/page and presentation state; it makes no provider-discovery, availability, semantic-AI, or booking-mutation call. Exhaustion returns a contextual prompt to choose another date and does not search automatically.

Selection by ordinal or spoken time is resolved only by `AivaV2DisplayedSlotSelectionResolver` against the current displayed page. It binds only a canonical slot reference already in that page. Invalid or ambiguous selection clarifies without reload or state change.

An explicit accepted date change retains tenant, patient, and provider and invalidates dependent availability, offset, selected slot, and slot-bound confirmation. A daypart/time change retains provider and date and uses existing availability behavior; dependent selection state is invalidated as required. A different provider request retains the current provider and draft and returns the existing Phase 3 handoff; it does not switch or reload availability.

## Compatibility and migration boundary

The Voice adapter is authoritative for AWAIT_SLOT turns. Gateway fast paths and kernel operations remain compatibility authorities for non-Voice conversation and non-slot workflows; duplicate Voice slot interpretation must not run after the adapter has selected an action. The kernel remains the downstream operation executor and `latestAvailabilityResult` pagination owner. Any compatibility branch left active will be identified and marked **COMPATIBILITY — PHASE 6 REMOVAL**; no broad gateway/kernel refactor is in scope.

## Tests and limitations

Coverage exercises the predicate, SHOW_MORE/exhaustion, explicit future-date distinction, displayed selection, date/daypart changes, provider-switch preservation, and real DotVoice transcript flows. DotVoice validates final transcript input and session continuity, not acoustic STT accuracy. SHOW_MORE does not reload availability or invoke discovery, AI, or mutation. Exhaustion asks whether another date is wanted; it never searches automatically. Provider switching remains deferred to Phase 3. Existing numeric/time normalization limits remain unchanged; the `1930`/`19:30` normalization issue is outside this bounded change. The kernel's preparation-only FIND_NEXT admission routes to existing non-mutating availability preparation and did not modify availability business semantics.

## Compatibility branches

- **AUTHORITATIVE:** `AivaV2VoiceSlotContext` predicate and AWAIT_SLOT adapter decision before generic fallback.
- **AUTHORITATIVE:** `AivaV2DisplayedSlotSelectionResolver` for selection restricted to the displayed page.
- **COMPATIBILITY — PHASE 6 REMOVAL:** gateway fast paths when invoked outside the adapter's AWAIT_SLOT path. They remain for existing non-Voice callers.
- **COMPATIBILITY — PHASE 6 REMOVAL:** downstream kernel operation executor and availability recovery paths. They execute the single operation selected upstream; they are not Voice slot-recognition authorities.
- **UNCHANGED OWNER:** `SessionProjection.latestAvailabilityResult` owns slots and pagination; no duplicate pagination state was introduced.

## Validation

Validation results:

- DotVoice inbound: 26/26 passed.
- Voice adapter: 74/74; language adapter: 32/32; authorized booking: 5/5; kernel: 40/40.
- Architecture tests: 7/7.
- Integration retry: availability boundary 1/1 and real transactional reliability 9/9 passed with access to the existing Docker daemon. The first broad run's two Testcontainers startup errors were infrastructure-only and cleared on retry.
- The authoritative selector's raw run covered 532 tests with 12 assertion failures, 2 infrastructure errors, and 17 skipped. After the integration retry (which replaces the two failed class initialization executions with 10 successful test executions), the effective total is 540 covered: 511 passed, 12 persistent assertion failures, 0 errors, 17 skipped. All 12 assertion-failure identities are unchanged from the accepted Phase 1 baseline; no new persistent failure remains.
- `git diff --check`: passed.

No Docker rebuild/restart was performed. The 12 unrelated baseline assertions remain outside Phase 2 scope. Remaining dependency: Phase 3 owns actual provider switching. No complete future FSM, time parser expansion, or Phase 7 observability/presentation redesign is included.
