# AIVA V2 Voice Conversational Context Routing

## Scope

This specification covers deterministic conversational routing and session context retention for the authorized DotVoice AIVA V2 path. It does not change booking execution, authorization, patient or tenant binding, OTP, idempotency, final confirmation, stale-slot validation, shared Web, or alternate Voice behavior.

## Routing rules

- A future appointment availability question that contains a date/daypart and availability wording routes to read-only `FIND_SLOTS`.
- Existing appointment lookup is recognized only from explicit patient ownership/status/history wording and is routed to the existing protected `FIND_APPOINTMENTS` boundary.
- Explicit provider/date/daypart values remain authoritative across clarification. Provider switching carries the explicit date/daypart into the pending switch record and clears provider-bound availability/slot selection when accepted.
- Active-provider pronouns and canonical same-provider mentions reaffirm the current provider; only a different canonical provider or an explicit change-provider command can start a switch. Same-turn date/daypart/time values continue to be applied.
- A pending switch decline clears the pending target and retains the current provider. Useful date/daypart/time and a distinct third-provider request in the same turn are processed without losing the retained booking context.
- `AWAIT_PROVIDER` accepts tenant-canonical provider names and recognized specialties. Specialty choice resolves against tenant-scoped provider data; unrelated or low-confidence replies leave provider and temporal context unchanged.
- `YES` and `NO` answer the exact pending provider-switch clarification. A provider name that repeats the requested target confirms that switch; a different canonical provider replaces the pending target and asks for confirmation of the replacement.
- Context inspection, discovery, and explicit date/daypart changes are handled before generic active-booking continuation. Read-only inspection never invokes booking mutation. Provider/date/daypart changes clear selected slots as appropriate; pagination preserves the existing provider/date/daypart and advances the retained page once.
- Courtesy close and unsupported protected-action routing retain their existing precedence and behavior.

## Conversation controls and collection precedence

Global conversation controls are resolved before state-specific collection handling, including `AWAIT_SLOT`. The order is courtesy close, cancellation of the current uncommitted booking draft, context changes, read-only inspection, repeat controls, pending clarification answers, state-specific answers, then generic interpretation/recovery. Existing appointment cancellation remains unsupported and cannot be mistaken for draft cancellation.

- Repeating while a slot page is active renders the same retained canonical page and leaves its offset unchanged. `SHOW_MORE` is distinct and advances the retained page exactly once without reloading availability when the snapshot remains valid.
- Repeating in date, daypart, provider, provider-switch, or final-confirmation state repeats that exact prompt/summary without mutating context.
- Slot-page controls accept Hindi STT variants such as “फिर से स्लॉट बताइए”, slot declines including “इनमें से कोई नहीं”, and provider/date inspection questions including “ये किस डॉक्टर के स्लाइस है?”. A retained display page is the source of truth for provider/date answers.
- “मुझे डॉक्टर बदलना है” is recognized as a control before provider extraction. Relative chained dates (“उसके अगले दिन”, “एक दिन बाद”, “next day”) resolve from the retained user-selected availability date. Time ranges are filtered against canonical availability when supported; otherwise the response remains localized.
- A combined current-draft cancellation and courtesy close clears the uncommitted draft and closes the call. Compatibility fallbacks for slot selection follow the persisted Hindi/Hinglish presentation mode.
- Provider/date/daypart changes preserve other valid context and invalidate provider/date/daypart-bound slot state. Provider changes remain subject to the existing confirmation and canonical resolution flow.
- Explicit discovery and context-inspection turns stay read-only during a draft. Inspection answers the active provider, retained date, or selected time without modifying the draft.
- Declining currently displayed slot options is a handled conversational action and offers more slots, context changes, or stopping the current booking.
- A read-only date may seed a booking draft only when its persisted availability cursor marks user-selected date context and the snapshot is still valid. The adapter does not promote a system/default date into the booking draft.

## Language behavior

Every response uses the persisted session presentation mode. Hindi fallback responses are Hindi-rendered, including the generic booking-preparation boundary; Hindi daypart labels are `सुबह`, `दोपहर`, and `शाम`. Hinglish and English retain their existing presentation conventions.

## Placement and dependencies

The owning boundary is the `api-bff` Voice application adapter. Changes remain in the existing adapter, session transition/model representation only if required by the current pending-switch snapshot, existing tests, and this specification. No persistence, migration, frontend, or domain-module changes are planned. The adapter may use the existing canonical tenant-scoped provider resolver and response renderer; business mutation and authorization dependencies remain unchanged.

## Validation

Tests cover availability-versus-history routing, date/daypart retention through provider clarification, provider switch accept/decline/replacement, context inspection and discovery during booking, slot invalidation on provider/date/daypart changes, localized responses, courtesy close, identity retention, and idempotent confirmation. Run the focused Voice adapter/resolver/context/language/booking suites and the unchanged Phase 8 selector.
