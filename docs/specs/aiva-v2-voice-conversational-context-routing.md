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

## Language behavior

Every response uses the persisted session presentation mode. Hindi fallback responses are Hindi-rendered, including the generic booking-preparation boundary; Hindi daypart labels are `सुबह`, `दोपहर`, and `शाम`. Hinglish and English retain their existing presentation conventions.

## Placement and dependencies

The owning boundary is the `api-bff` Voice application adapter. Changes remain in the existing adapter, session transition/model representation only if required by the current pending-switch snapshot, existing tests, and this specification. No persistence, migration, frontend, or domain-module changes are planned. The adapter may use the existing canonical tenant-scoped provider resolver and response renderer; business mutation and authorization dependencies remain unchanged.

## Validation

Tests cover availability-versus-history routing, date/daypart retention through provider clarification, provider switch accept/decline/replacement, context inspection and discovery during booking, slot invalidation on provider/date/daypart changes, localized responses, courtesy close, identity retention, and idempotent confirmation. Run the focused Voice adapter/resolver/context/language/booking suites and the unchanged Phase 8 selector.
