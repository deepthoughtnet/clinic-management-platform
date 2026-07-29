# Discover Reusable Location Map Support

## Status

Approved for implementation foundation.

## Product Boundary

Jeevanam Discover owns reusable location selection and display for provider onboarding and public provider profile rendering. This is not a generic mapping platform and does not introduce provider-specific routing or map-provider coupling.

Out of scope:

- proprietary map SDKs
- hardcoded map tile URLs
- hardcoded geocoding URLs
- browser alerts
- unrelated profile redesigns

## Ownership

- Owning bounded context: Discover
- Backend domain module: `discover-domain`
- API adapter: `api-bff`
- Persistence ownership: Discover
- Migration owner: Discover, with Flyway executed by `api-bff`
- Frontend: `web-discover`

## Core Rule

Provider location data remains owned by the Discover provider profile aggregate.

Map display and location selection are presentation and capture concerns layered on top of the existing location records.

## Supported Behavior

- provider onboarding Step 5 location capture
- compact read-only public profile map display
- map click selection
- draggable marker selection
- current location capture via explicit user action
- address search and geocoding when configured
- autosave after confirmed coordinate changes
- restore coordinates after draft resume

## Persistence

Discover location rows remain the owner of provider addresses and operational fields, with optional latitude and longitude coordinates added to the existing location record family.

Expected update path:

- `discover_provider_locations`

## API Surface

Provider onboarding and public profile responses project location coordinates alongside the existing address model so frontend consumers can render maps without duplicating location state.

## Frontend Ownership

Reusable browser components live in `web-discover/src/components/location/**` and are consumed by provider onboarding, provider preview, and public profile pages.

## Validation Expectations

Backend validation must verify:

- optional coordinates
- latitude range `-90` to `+90`
- longitude range `-180` to `+180`
- tenant and ownership checks for write paths

Frontend validation must verify:

- address remains editable even if map services fail
- search, current-location, and drag interactions remain optional
- autosave only occurs after confirmed coordinate changes
- read-only map rendering works in preview and public profile views

## Test Expectations

Required tests for implementation:

- location persistence and projection tests
- provider onboarding Step 5 map selection tests
- draft resume and autosave tests for coordinates
- public profile map display tests
- validation tests for coordinate range handling
- frontend component tests for picker and display behavior

