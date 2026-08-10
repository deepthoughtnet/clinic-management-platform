# Discover Home Search Intent Selector and Global Search

## Status

Approved.

## Product Boundary

Jeevanam Discover owns the public home search intent selector and the global search results experience.

This batch updates the Discover Home hero search so patients can choose an entity-aware intent before submitting:

- Any
- Doctors
- Clinics
- Hospitals
- Services

It also adds a lightweight global results page for the Any intent.

Out of scope:

- Healthcare persistence
- Discover public projection shape changes
- DoctorPracticeAssociation semantics
- booking capability/linking
- Care
- patient identity
- appointment booking
- slot generation
- backend search algorithm redesign
- schema changes
- migrations

## Ownership

- Owning bounded context: Discover
- Backend domain module: none
- API adapter: none
- Persistence ownership: none
- Migration owner: none
- Frontend: `web-discover`

## Core Rules

- The home selector is a single-selection radio group styled as compact pills.
- Switching intents does not navigate.
- Search text and selected location are preserved while the intent changes.
- Submit only occurs on Search button press or Enter in the query field.
- Home preview remains unchanged when no explicit search has been submitted.
- Any routes to `/discover/search`.
- Doctors routes to the canonical doctor directory.
- Clinics routes to the canonical clinic directory.
- Hospitals routes to the canonical hospital directory.
- Services routes to the existing canonical speciality/service discovery flow.
- Global search is a composed read-only result page that groups existing public category search results.
- No fake classification or heuristic entity detection is introduced.

## Compatibility Plan

- Existing directory pages remain authoritative for category-specific filters, sorting, and pagination.
- The global search page is additive and does not replace the category pages.
- Existing public APIs remain the source of truth.
- No backend endpoint is added unless composition becomes impossible.

## Validation Expectations

- Home search intent selector renders with accessible radio-group semantics.
- Query, city, and supported location context survive navigation.
- `/discover/search` renders grouped results and section-level view-all links.
- Empty global search shows one clean empty state.
- Browser back returns to Discover Home without changing the existing preview behavior.

## File Ownership Map

- `web-discover/src/pages/discovery/PublicDiscoveryPages.tsx`
- `web-discover/src/routes.ts`
- `web-discover/src/App.tsx`
- `web-discover/src/styles.css`
- `web-discover/test/discover-homepage-search-intent.test.mjs`

## Test Expectations

- Selector defaults to Any.
- Switching pills preserves search text.
- Switching pills does not navigate.
- Submit on Any routes to `/discover/search`.
- Submit on Doctors, Clinics, Hospitals, Services routes to the corresponding canonical pages.
- Global search groups existing public category results.
- Empty global search shows a single empty state.
- View-all links preserve query and location context.
