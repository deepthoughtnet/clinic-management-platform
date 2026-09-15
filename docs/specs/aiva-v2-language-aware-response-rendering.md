# AIVA V2 Language-Aware Response Rendering

## Status

Approved additive presentation change for the existing AIVA V2 patient-portal
conversation path. Kernel decisions, booking tools, and domain behavior remain
language-neutral and unchanged.

## Ownership And Placement

- Owning context: patient portal AIVA V2 presentation/orchestration.
- API module: `backend/api/api-bff`.
- Persistence and migration: none; presentation metadata is per turn and is not
  booking domain state.
- Frontend and voice: unchanged.
- Allowed dependencies: AIVA V2 response DTOs, language adapter metadata, Java
  locale/date formatting, and Spring component wiring.

## Behavior

Render supported structured response categories in English standard, Hindi
standard, or Hindi Hinglish. If a localized template is unavailable, preserve
the existing English response. Preserve opaque references and identifiers
exactly. Render dates from their existing `LocalDate` value; do not change
temporal values. Unsupported categories retain existing response text.

## Validation

Unit tests cover supported categories, presentation metadata flow, safe entity
preservation, and English compatibility. Focused AIVA V2 tests, the requested
API BFF Maven package build, and `git diff --check` are required.
