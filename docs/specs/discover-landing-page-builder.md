# Discover Structured Landing Page Builder

## Status

Approved for Phase 5 implementation foundation.

## Product Boundary

Jeevanam Discover owns the structured public landing-page builder for published providers. This feature is not a generic site builder.

Out of scope:

- drag-and-drop free-form editing
- arbitrary HTML or markdown rendering
- rich text block authoring beyond controlled titles and descriptions
- custom CSS or unrestricted layout overrides
- provider profile data duplication

## Ownership

- Owning bounded context: Discover
- Backend domain module: `discover-domain`
- API adapter: `api-bff`
- Persistence ownership: Discover
- Migration owner: Discover, with Flyway executed by `api-bff`
- Frontend: `web-discover`

## Core Rule

Landing-page configuration must remain independent from published provider profile data.

Published profile data supplies business truth.
Landing-page configuration supplies presentation rules.

## Supported Concepts

The landing-page model supports:

- templates: Solo Doctor, Family Clinic, Multi-speciality Clinic, Hospital
- sections: hero, about, services, doctors, departments, facilities, consultation modes, working hours, gallery, insurance accepted, awards and certifications, FAQs, contact and map, appointment CTA
- draft and published versions
- version history and rollback
- constrained theme settings: primary color, accent color, typography preset, button style, border radius preset

## Persistence

The Discover landing-page aggregate is modeled as owned Discover tables and versioned rows.

Expected table families:

- `discover_landing_pages`
- `discover_landing_page_versions`
- `discover_landing_page_sections`
- `discover_landing_page_themes`
- `discover_landing_page_templates`

Each published provider may have one landing-page aggregate tied to the provider profile.

## API Surface

Provider-facing APIs:

- `GET /provider/landing-page`
- `PUT /provider/landing-page`
- `GET /provider/landing-page/preview`
- `POST /provider/landing-page/publish`
- `POST /provider/landing-page/revert`
- `GET /provider/landing-page/versions`

Public API:

- `GET /public/landing/{slug}`
- `GET /discover/clinics/{slug}`
- `GET /discover/clinics/{slug}/home`

## Lifecycle

Draft configuration is editable only by the owning provider.

Publishing creates an immutable landing-page version.

Rollback restores a prior version as the active draft and requires an explicit publish step to affect the public page.

Preview renders the same section components and theme rules as the public page, but resolves draft configuration instead of the published version.

## Compatibility Plan

This phase is additive.

- Existing published provider profile data remains authoritative.
- Existing public profile routes continue to function.
- New landing-page URLs resolve to the published landing-page projection without replacing the provider profile model.
- `home` aliases are optional compatibility paths and must not create separate site records.

## Validation Expectations

Backend validation must verify:

- ownership checks for provider edits
- publication gating for public reads
- version history persistence
- preview generation from profile + draft configuration
- no unpublished data exposed publicly

Frontend validation must verify:

- template switching
- section enable/disable/reorder
- preview updates
- publish flow
- revert flow
- responsive rendering across desktop, tablet, and mobile

## Provider Workspace UX Constraints

- Provider preview must render persisted draft data only.
- Empty states must replace fabricated business content.
- Readiness and publication summaries must be rendered from backend DTOs.
- Provider-facing preview and public-profile routing must be derived from the persisted slug/public path, not from placeholder route values.
- The UI must distinguish unpublished draft preview from published public view.

## File Ownership Map

- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/landingpage/**`
- `backend/domains/discover-domain/src/test/java/com/deepthoughtnet/clinic/discover/landingpage/**`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/**`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/discover/**`
- `backend/api/api-bff/src/main/resources/db/migration/V128__discover_landing_page_foundation.sql`
- `web-discover/src/api/providerLandingPage.ts`
- `web-discover/src/pages/provider/ProviderLandingPagePage.tsx`
- `web-discover/src/pages/public/LandingPagePage.tsx`
- `web-discover/src/components/landing/**`

## Test Expectations

Required tests for implementation:

- landing-page persistence and version history tests
- ownership and publication gating tests
- public preview and public render tests
- API route and wiring tests
- frontend builder interaction tests
- frontend responsive rendering tests
- integration test covering provider edit, preview, publish, and public read
