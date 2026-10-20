# AIVA V2 Voice Doctor Schedule and Daypart Availability

## Status

Approved implementation scope supplied by the product request.

## Ownership and placement

- Appointment-domain owns recurring doctor availability, date-specific slot generation, exceptions, breaks, and bookability.
- `api-bff` adapts those application services through the existing read-only healthcare gateway and formats voice responses.
- AIVA V2 owns normalized daypart semantics and call-scoped conversational availability state.
- No persistence schema or migration change is planned; the existing V2 session store holds transient voice results.
- No frontend change is planned.

## Scope

- Add read-only `FIND_DOCTOR_SCHEDULE` for recurring configured consultation windows.
- Distinguish recurring schedule, effective availability on a date, and bookable appointment slots.
- Propagate canonical V2 daypart through the Voice tool input.
- Group available date-specific slots by canonical daypart and ask only about periods that have bookable slots.
- Present at most three slot times and continue from retained session results when the caller asks for more.
- Keep all Voice execution inside the existing allowlist; booking, cancellation, rescheduling, and patient-owned tools remain denied.

## Constraints

- Reuse `AppointmentService.listDoctorAvailabilities` and `AppointmentService.listSlots`.
- Do not query repositories from Voice or use free-text location working hours as doctor schedule.
- Do not alter AIVA V1 daypart behavior.
- Use current V2 ranges: morning before 12:00; afternoon 12:00 through before 17:00; evening from 17:00.
- Do not infer that a doctor does not consult from an empty bookable-slot result.
- Do not add a second slot engine or a second progress/filler engine.

## Pre-UAT Hardening Addendum

- Missing slot dates are clarified rather than defaulted to tomorrow. The existing V2 `SessionProjection.latestAvailabilityResult` carries doctor, date, daypart, pending continuation and page offset between turns.
- Relative date forms already supported by the V2 temporal normalizer are reused; prospective slot requests interpret ambiguous Hindi/Hinglish `kal` as tomorrow in the call timezone.
- V1 retains its separate daypart behavior (morning 08:00–12:00, afternoon 12:00–16:00, evening thereafter); this known V1/V2 inconsistency is documented only and is not changed here.
- Recurring schedule, date-effective schedule (including appointment-domain unavailability), and bookable slots use separate query types. Date-effective schedules do not infer consultation from free-slot counts.
- Date-specific availability uses `ClinicTimeZoneResolver` and the appointment service. The current appointment-domain schedule record is doctor/weekday based and has no clinic/location scope; Voice must not merge a fabricated location assignment.
- The V2 session stores pagination and yes/no offers in its existing availability result cursor. Voice speaks at most three slots and asks only among dayparts with bookable results.
- No canonical next-available-date service is currently wired into this read-only Voice path; future-date scanning is intentionally deferred. DotVoice filler/progress audio is also deferred.
- No persistence migration or frontend change is needed.

## Confirmed Regression Fix Addendum

- English doctor-name extraction strips leading interrogative/discourse framing before parsing a candidate and preserves the actual name; cross-script matching remains shared.
- Schedule semantics recognize Devanagari `सिटिंग टाइम` before generic time/slot evidence. Hindi phonetic dayparts `मॉर्निंग`, `आफ्टरनून`, and `इवनिंग` normalize to canonical V2 dayparts.
- Response-language selection follows the current utterance when it is clearly English, even when the call locale is `hi-IN`; Devanagari and mixed-language utterances keep Hindi/Hinglish handling.
- Missing-date requests remain clarification-only; relative date and daypart continuation reuse the existing availability cursor.
- A show-more utterance while the cursor is awaiting a daypart choice repeats the available-daypart choice and does not page. Once selected, existing page offset and three-item limit continue.
- Short provider references resolve only against candidates already retained in the current tenant/call session. Multiple matching candidates remain ambiguous.
- A new provider-discovery, schedule, transaction, or close intent supersedes and clears stale slot continuation state; selected provider context remains unless the session is closed.
- Same-day Voice slot presentation additionally excludes slots whose start time is at or before tenant-local now. This is deliberately enforced at the Voice read-only gateway boundary; appointment-domain behavior is unchanged.
- Blocked cancellation, reschedule, and appointment lookup intents receive intent-specific read-only responses. No mutation capability is enabled.
- Appointment possibility wording (for example, “appointment mil sakta hai?”) is treated as date-specific availability, not appointment-history lookup. A bare doctor-name-plus-appointment noun gets a bounded clarification instead of being guessed as discovery, booking, or history.
- When a booking request is blocked, an explicit/currently resolved doctor is retained in the existing call-scoped availability context so a subsequent request to see slots can ask for a date and resume with that doctor. This transient context expires under the existing session rules.
- Pending-date answers reuse the shared temporal normalizer, including Hindi word-number plus month dates (for example, “बाईस अक्टूबर”); a new explicit schedule intent still supersedes the pending date.
- Date-aware generic discovery across all doctors is not provided by the current canonical availability service; it requires a doctor-specific identifier. No cross-doctor availability scan was added. STT near-name suggestions remain deferred because no bounded, confidence-bearing tenant-scoped suggestion service is currently exposed to this path.

## Deterministic Continuation Addendum

- A resolved single doctor plus a relative date and generic availability wording is a date-specific slot query; an undated provider-availability question remains `FIND_DOCTOR`. Ambiguous provider context asks for doctor clarification.
- Pending availability flows consume an explicit date even when the utterance begins with a negative token. The new date replaces the prior date, clears inherited daypart and paging offset, and retains the current-call doctor context.
- A negative-prefixed daypart response (for example, “नहीं, शाम का बताइए”) is treated as an actionable daypart selection, not as declining the continuation.
- Absolute date answers after date clarification preserve the doctor stored in the existing availability cursor. No parallel continuation store or schema change is introduced.

## Validation

Focused tests cover schedule lookup, date-effective availability, daypart parsing and filtering, available-daypart prompts, three-slot paging, cross-turn doctor context and ambiguity, plus existing Voice read-only and discovery regressions. Validate API package and `git diff --check`.

## Live Session Continuity Addendum

- A successful `FIND_DOCTOR` projection remains the source for later unambiguous current-call provider context, including a single specialty-filtered result. Multiple candidates remain ambiguous; no provider is auto-selected.
- The existing availability cursor retains doctor identity independently of date, daypart, and paging state. Replacing a date after a real daypart slot query clears the old daypart and page offset without clearing the provider.
- An explicit doctor plus date and availability wording routes to date-specific availability; undated provider availability remains discovery. “Appointment slot” is an explicit slot request, while a bare doctor-plus-appointment noun retains bounded intent clarification.
- The existing pending-appointment-intent cursor carries resolved doctor/date through clarification. Choosing read-only slots resumes availability; choosing a mutation remains blocked and preserves only safe conversational doctor/date context.
- Existing temporal normalization recognizes the common Latin transliteration `parso` as day-after-tomorrow. No provider-specific aliases or fuzzy name matching are introduced.

## Deployed Runtime Context/Language Addendum

- Availability evidence includes the observed Devanagari transliteration `अवेलेबल`; date plus that evidence is evaluated as effective-date availability regardless of question punctuation. The same token is recognized after explicit Devanagari doctor names. Without a date, provider availability remains discovery.
- A single specialty-filtered doctor result is retained from the real read-only gateway projection and reused on the next turn under the same provider-call session key; the projection continues to distinguish the filtered candidate count from the original source count.
- Doctor discovery speech uses the response language selected for the current utterance, including English utterances during `hi-IN` calls. Date-only no-schedule responses do not claim a specific time; a time/daypart is named only when one was requested.

## Active Doctor Session Invariant

- `AivaV2Models.SessionProjection.activeVoiceDoctor` stores the call-scoped active provider independently from latest search candidates and availability/paging state.
- A single provider result establishes or refreshes the active doctor. An ambiguous provider search clears the active selection while preserving candidate results for clarification.
- A blocked booking request without a repeated doctor name reuses the active doctor for its read-only response and retains that doctor for subsequent availability requests. The booking remains blocked and no transaction executes.
- Empty provider projections from schedule/slot tools and date/daypart/cursor/paging resets do not clear active doctor identity. Session close/reset clears it through the existing lifecycle.
- This is transient V2 session state; no persistence migration or parallel store is introduced. The existing DotVoice `stream_stop` end hook removes the tenant/call-scoped V2 session after the independent communication diagnostic snapshot is retained.
