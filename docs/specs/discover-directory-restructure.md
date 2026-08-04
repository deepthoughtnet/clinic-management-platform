# Discover Directory Restructure

## Status

Approved.

## Product Boundary

Jeevanam Discover owns the public directory experience for doctors, clinics, hospitals, and specialities.

This batch restructures the browser presentation and URL-state behavior for the following routes:

- `/discover/doctors`
- `/discover/clinics`
- `/discover/hospitals`
- `/discover/specialities`

Out of scope:

- provider onboarding
- provider authentication or sessions
- review, approval, or publication workflow
- booking completion logic
- public provider detail page semantics unless a shared card or safe media helper requires a minimal change
- database schema changes
- Flyway migrations
- demo providers outside the existing home-page UAT flag
- fake public catalog data

## Ownership

- Owning bounded context: Discover
- Backend domain module: none for this batch
- API adapter: none for this batch
- Persistence ownership: none for this batch
- Migration owner: none for this batch
- Frontend: `web-discover`

## Core Rules

- Use only real public catalog data already returned by the public directory APIs.
- Keep URL state as the source of truth for search, filters, sort, location, and pagination.
- Preserve current public routes and booking handoff behavior.
- Keep temporary demo cards limited to the home-page UAT feature flag.
- Never surface raw MinIO URLs or fake provider records in directory pages.
- Keep the shared Discover footer on all directory pages.

## Supported UX

The directory experience is split into a shared shell and page-specific compositions.

Shared shell expectations:

- global header
- page identity with eyebrow, title, and supporting text
- smart search with integrated location controls
- page-specific popular categories or quick filters
- results toolbar with count, active location, active filter summary, sort, and clear actions
- optional sticky filters on desktop
- mobile filter drawer or modal
- directory results
- truthful empty states
- footer

Page-specific expectations:

- Doctors: compact comparison-oriented cards, specialist discovery emphasis, booking emphasis
- Clinics: one-result-per-row vertical cards, practice/team/facility emphasis, nearby and service emphasis, bounded load-more continuation
- Hospitals: one-result-per-row vertical cards, departments/capabilities emphasis, bounded load-more continuation
- Specialities: icon-grid browsing, A–Z navigation where useful, specialist discovery hierarchy, AIVA coming-soon panel

## Compatibility Plan

This batch is additive in behavior and layout.

- Existing public directory APIs remain the source of real data.
- Existing public booking handoff remains unchanged.
- Existing public provider detail pages remain reachable.
- No fake results are introduced to pad empty views.
- If a public media URL is not safe for browser rendering, the card omits the image instead of exposing the source.

## Validation Expectations

Frontend validation must verify:

- search parameters remain URL-synchronized
- location can be changed without blocking search
- filters can be applied and cleared
- count, sort, and empty states reflect real loaded data
- directory cards remain type-aware and page-specific
- mobile filters open and close accessibly
- footer renders on all four pages
- public-safe media URLs are used
- no demo providers appear in the directory routes

## File Ownership Map

- `web-discover/src/pages/discovery/PublicDiscoveryPages.tsx`
- `web-discover/src/components/DiscoveryComponents.tsx`
- `web-discover/src/styles.css`
- `web-discover/src/routes.ts` if route labels or footer metadata need adjustment
- `web-discover/test/discover-directory-restructure.test.mjs`
- `web-discover/test/discover-phase2.test.mjs`
- `web-discover/test/discover-homepage-premium.test.mjs`
- `web-discover/test/discover-foundation.test.mjs`
- `web-discover/test/discover-location-map.test.mjs`
- `web-discover/test/discover-homepage-demo-providers.test.mjs`

## Test Expectations

Required tests for implementation:

- URL-state synchronization tests for each directory page
- location interaction tests
- filter drawer open/close tests
- result-count and sort-state tests
- empty-state action tests
- public-safe media tests
- footer presence tests
- page-specific card layout tests
- bounded load-more tests for clinic and hospital result lists
- regression tests for home, public detail pages, provider auth header, patient login, and booking handoff
