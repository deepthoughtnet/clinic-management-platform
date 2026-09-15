# AIVA V2 Stage 3D Closure Certification

## Status

Approved certification-only scope for the remaining Stage 3 language/session checks.

## Scope and placement

Certification belongs in `api-bff` AIVA V2 tests. Use the real `AivaV2ConversationService`, adapter registry, gateway, kernel, session store, and renderer; external patient/provider data and the semantic-provider boundary may use controlled fixtures. No domain, persistence, authorization, tenant-isolation implementation, provider-routing, or voice changes are planned.

## Acceptance

- Service calls prove isolation across patient, tenant, and conversation key dimensions, including presentation style.
- Responses from the four certified workflows are structured and render in English, Hinglish, and Devanagari.
- Explicitly identified suspend/resume/abandon/context-answer categories are classified as core or ancillary before any mapping change.
- Existing relative-date, weekday, daypart, and exact-time semantics are certified without adding new interpretation rules.
- Standalone date/time/ordinal/confirmation turns preserve conversation presentation style through the real service.

## Validation

Run focused Stage 3D tests, all `*Aiva*Test` API BFF tests, the API BFF reactor package, and `git diff --check`.
