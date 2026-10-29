# AIVA V2 Voice Provider Adoption and Presentation Mode

## Placement

- Owner: AIVA V2 Voice conversation/application orchestration.
- Module: `backend/api/api-bff`.
- Persistence: existing `AivaV2SessionStore` / serialized `SessionProjection`.
- Appointment and provider persistence: unchanged, owned by existing domains.
- Frontend and migration: none.
- Allowed dependencies: Voice adapter to the tenant-scoped transactional provider resolver and existing session/language models.

## Provider ownership invariant

Read-only discovery projections are identity evidence. Every provider newly
written into `BookingDraft.selectedProvider` must first be resolved by doctor
ID in the trusted tenant using the canonical transactional provider resolver.
The draft stores only that result. Public booking references cannot cross into
a tenant-local CARE_PRIVATE booking draft. Existing copied drafts retain their
already-bound provider until an explicit provider adoption occurs.

## Presentation ownership

The conversation presentation mode (`HINDI`, `HINGLISH`, or `ENGLISH`) is
stored on `SessionProjection`, established by the first meaningful turn, and
retained across session transformations. A later explicit language-switch
request may change it. Mixed-language turns do not change it. Voice rendering
uses this session mode for deterministic booking responses.

## Compatibility and validation

Older session projections with no mode remain readable and initialize mode on
the next meaningful turn. The change is additive, requires no migration, and
does not alter appointment mutation, trust policy, availability, slot
selection, or non-BOOK operation gates. Validate canonical provider adoption,
mode persistence/Redis round-trip, inbound booking responses, and existing
AIVA V2 regressions.
