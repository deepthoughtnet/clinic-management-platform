# Discover Homepage Demo Provider Cards

## Status

Proposed for local/UAT visual validation.

## Scope

The Discover home page may append temporary demo cards for doctors, clinics, and hospitals when the home-page demo flag is enabled.

This is a visual validation aid only. It must not affect:

- public directory APIs
- booking flows
- provider onboarding
- public provider detail pages
- backend persistence

## Ownership

- Owning bounded context: Discover
- Frontend module: `web-discover`
- Demo data source: frontend-only constants

## Rules

- Real public provider cards always render first.
- Demo cards are only appended on the Discover home page.
- Demo cards must never be returned by backend APIs or persisted.
- Demo actions are disabled and must not navigate to real routes.
- The feature defaults to enabled only in local/UAT-style environments or when explicitly overridden.

## Validation

- Home-page demo cards render only when the flag is enabled.
- Directory and detail routes remain unchanged.
- Demo cards are visually distinct but subtle.
- Production builds do not expose demo cards unless explicitly enabled.
