# AIVA V2 Voice Phase E — Provider Bookability Consistency

## Placement and dependency plan

- Owning context: Patient Portal AIVA V2 provider/booking orchestration.
- API module: `backend/api/api-bff`; Voice and booking adapters remain inbound/application orchestration.
- Persistence owner: existing tenant doctor profile and appointment schedule services; no new persistence model.
- Migration owner: none; no schema change is planned.
- Frontend area: none.
- Allowed dependencies: Voice adapter → transactional execution boundary → existing V2 booking provider resolver; existing provider discovery → typed source/capability projection. The kernel retains its bookability gate and canonical availability tools.
- Planned files: `AivaV2VoiceToolExecutor`, `VoiceReadOnlyAivaService`, `AivaV2VoiceConversationAdapter`, `AivaV2TransactionalExecutionBoundary`, `AivaV2TransactionalKernel`, and focused booking/inbound tests.

## Problem

Read-only Voice provider projections do not carry all booking capability data. Mapping an omitted internal CARE_PRIVATE capability to `onlineBooking=false` incorrectly makes “not loaded” indistinguishable from “not bookable”. Public catalog capability, by contrast, is authoritative and must preserve explicit negative results.

## Decision

- Preserve provider source and any known capability metadata in read-only provider options.
- Represent unavailable capability metadata as unknown (`null`) rather than authoritative false.
- Before adopting a discovered internal/public provider into a booking draft, resolve the exact doctor ID through the existing tenant-scoped V2 booking provider resolver using the trusted call tenant. Adopt the canonical candidate only if ID, tenant, and available clinic identity match.
- A provider that fails canonical tenant resolution retains its prior capability state; booking tools fail closed when capability is unknown or false.
- Public catalog `canBookOnline` remains authoritative. The canonical local provider may supersede it only when the same doctor resolves in the trusted tenant as a CARE_PRIVATE provider.
- Do not change appointment mutation, Web authorization, session ownership, or the kernel’s NOT_BOOKABLE rule.

## Validation

Verify real inbound Voice adoption, local CARE_PRIVATE availability with no external booking target, a genuinely non-bookable public provider, Web/Voice capability parity, Phase A/B/C/D regressions, broad AIVA tests, Maven package, and `git diff --check`. Do not rebuild local containers.
