# AIVA V2 Canonical Language Boundary

## Status

Approved implementation scope for Phase 1 input normalization.

## Goal

Normalize English, Hinglish, and Devanagari Hindi turns into typed, language-neutral semantic facts before the AIVA V2 decision gateway and transactional kernel. Preserve response-language/style as presentation metadata outside booking state.

## Contract

`NormalizedUserTurn` carries raw and normalized text, detected and response language/style, canonical semantic text, doctor and specialty references with raw spans and canonical queries, temporal resolution with raw span/status/`LocalDate`/relative kind/year inference, exact time, daypart, ordinal, confirmation, pagination, ordered controls, and coarse intent.

The language adapter and locale temporal normalizer own locale-specific interpretation. The gateway receives canonical semantic text and typed facts; trusted adapter facts take precedence over provider omissions or conflicting values. Raw input is bounded trace/context only. The kernel continues receiving existing canonical decisions and date/time values; no domain or business semantics change in this phase.

Gateway precedence is operation-aware: booking and availability criteria are merged into `bookingPatch`; appointment lookup, cancellation source resolution, and reschedule source resolution are merged into `lookupFilter`. A conflicting provider value is overridden by the trusted current-turn fact and may be traced by safe field category only, without logging the entity text or temporal span.

## Temporal rules

Yearless dates use the clinic-local reference date: resolve in its year unless the candidate date precedes the reference date, in which case infer the next year. Invalid and unresolved expressions preserve the raw span and explicit status. Locale month names are mapped to `java.time.Month`; kernel code does not parse localized strings.

## Presentation continuity

Response language/style are held in the expiring AIVA conversation session presentation store, separate from booking/domain state. A turn with no letters (for example, `20:00`) retains the previous presentation choice.

## Out of scope

Localized structured response payload redesign, domain/business changes, authorization, tenant isolation, provider routing, persistence/migrations, and voice/audio transport.

## Validation

Adapter and temporal tests, gateway precedence/input tests, vertical language-equivalence certification, existing English AIVA regressions, API BFF reactor package, and `git diff --check`.
