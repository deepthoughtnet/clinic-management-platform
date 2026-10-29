# AIVA V2 Voice Clarification and Recovery — Batch 1

## Status

Approved implementation scope for bounded, call-scoped clarification state.

## Scope

- Extend the existing AIVA V2 `SessionProjection` with a minimal Voice recovery state.
- Bound repeated unresolved clarification to two prompted attempts; the next unresolved turn records an escalation decision.
- Recognize the existing deterministic human-assistance control in the Voice adapter and record an immediate in-session escalation decision.
- Preserve `activeVoiceDoctor` during clarification and clear recovery state on successful resolution or session close/reset.
- Emit safe operational diagnostics for clarification kind, count, reason, and escalation decision.

## Constraints

- No callback/receptionist task persistence, migration, or callback promise.
- No transactional Voice execution and no V1 changes.
- No new session store or fuzzy matching.
- Reuse existing V2 language adapters, session store, and call lifecycle.

## Placement

- AIVA V2 Voice application/session orchestration in `backend/api/api-bff`.
- State is serialized with the existing AIVA V2 session projection; no separate persistence owner or schema change.
- No frontend changes.

## Recovery semantics

1. First unresolved occurrence asks a focused question with at most three bounded options.
2. A second unresolved occurrence of the same kind/reason asks a narrower clarification.
3. A third unresolved occurrence records an in-memory escalation decision and stops the clarification loop; no task is created and no callback is promised.
4. A direct human-assistance control records an immediate escalation decision.
5. A successful tool turn, a resolved new intent, or session end clears the recovery state. Session end also clears the active doctor through the existing lifecycle.

## Unsupported-request routing clarification

Voice provider discovery is selected only when the current turn contains positive provider evidence: provider/doctor vocabulary, an extracted doctor reference, or a recognized specialty. Other unsupported requests (for example insurance, billing, parking, or complaints) remain `UNKNOWN` and use the bounded `UNSUPPORTED_REQUEST` clarification state; they must not be sent to `FIND_DOCTOR` as a fallback. Existing supported discovery with zero matches remains a doctor-discovery result.

## Spoken date rendering

Canonical `LocalDate` values remain unchanged. Speech-facing renderers use a shared language-aware formatter with full month names (Hindi month names for Hindi, English month names for Hinglish and English); compact UI/diagnostic formats remain separate.

## Voice transaction-readiness observability

- Expose safe artifact identity (application version/build timestamp where packaged, and commit/release identifiers only from explicitly whitelisted deployment metadata).
- Persist bounded structured per-turn semantic, effective-tool-input, and response-source diagnostics in the existing Communication Test snapshot. The diagnostic object must not contain caller phone or patient data.
- Report parser-extracted exact time/range separately from effective tool inputs. Exact/range slot filtering is unavailable unless the existing read-only healthcare gateway supports it.
- Preserve the read-only mutation guard. This observability addition does not authorize booking, cancellation, or rescheduling.
- Maven Spring Boot build-info supplies application version and build timestamp. Deployment may supply `AIVA_BUILD_COMMIT` (or `GIT_COMMIT`) and `AIVA_RELEASE_TAG` (or `RELEASE_TAG`); equivalent JVM properties are `-Daiva.build.commit=<sha>` and `-Daiva.release.tag=<tag>`. The runtime validates and exposes only these whitelisted values; never hardcode a commit SHA into source.

## Voice exact-time and time-range slot filtering

- Preserve `AppointmentService.listSlots(...)` as the canonical source of generated, bookable slots.
- Apply Voice exact-time and inclusive time-range filters only to the canonical slot list returned by `HealthcareAiReadOnlyToolGateway`; do not query repositories or generate slots in the Voice layer.
- Exact-time match semantics are equality on the canonical slot's local start time. A unique match is exposed as a read-only candidate only; no appointment mutation is performed.
- Time-range semantics are inclusive on slot start time: `start >= requestedStart && start <= requestedEnd`.
- A range accompanied by a recognized afternoon/evening daypart is normalized within that period; an unqualified clinic-slot range wholly within 3–8 is interpreted in the conventional PM band (so “4 to 6” becomes 16:00–18:00). Explicit morning wording retains AM values; explicit 24-hour values remain unchanged.
- If an exact time has no match, provide at most three nearest canonical alternatives; if it has multiple matches, do not choose arbitrarily.
- Carry the selected read-only constraint and filtered slot page in the existing `AvailabilityResult` session projection so show-more uses the same criteria.
- Keep booking/cancellation/reschedule blocked. This batch does not make Voice transaction-ready for booking until any remaining confirmation/authorization requirements are separately completed.

## Validation

Cover sequential unknown retries, ambiguous doctor options and resolution, active doctor preservation, direct human request, recovery reset after tool success, Redis serialization, and end-session cleanup.
