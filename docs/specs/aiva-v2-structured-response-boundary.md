# AIVA V2 Structured Response Boundary

## Status

Approved implementation scope for Phase 2 response-contract migration.

## Goal

Keep kernel decisions and tool outcomes language-neutral while returning a typed semantic response and facts to the API BFF renderer. The renderer owns EN, Hinglish, and Devanagari presentation. Existing `MessageResponse` JSON fields remain compatible; `structuredResponse` is additive and rendered text continues to populate `assistantMessage`.

## Placement and dependencies

The API BFF owns the response model, kernel-result mapping, locale renderer, and transport compatibility. This change depends only on existing AIVA API BFF contracts and appointment/provider facts already exposed to that layer. It does not change domain services, persistence, authorization, provider routing, frontend code, or voice transport.

## Contract

`AivaStructuredResponse` carries a semantic `ResponseType`, an explicit sealed payload variant, and interactive actions. Payload variants cover provider choices, booking prompts/availability/confirmation/success, appointment lookup, cancellation, reschedule, safe clarification/failure/stale outcomes, and call-to-book. Appointment references, slot references, provider display names, and underlying `LocalDate`/`LocalTime` values remain unchanged.

For migrated response types, the renderer consumes only the structured type and payload. It never parses English prose and does not reconstruct cancellation or reschedule content from booking state. English remains the fallback renderer for an unsupported locale; legacy `assistantMessage` is reserved for unmigrated `LEGACY` categories.

## Compatibility and boundaries

`assistantMessage`, `responseCategory`, state, and actions remain in `MessageResponse`. `structuredResponse` is additive. Kernel-generated English copy may remain temporarily as compatibility metadata, but migrated rendering is determined by structured facts. No localized wording is added to domain services or tools.

## Validation

Renderer and structural tests, booking/lookup/cancellation/reschedule regressions, Phase 1 language-boundary tests, API BFF reactor package, and `git diff --check`.
