# Discover Provider Onboarding Platform

## Status

Approved for Phase 3 implementation foundation.

## Scope

Jeevanam Discover owns provider onboarding for individual doctors, clinics, and hospitals before Healthcare tenant activation. This phase supports draft creation, resume, progressive completion, structured provider data, document upload, preview, submission, lifecycle history, and backend events/placeholders for notifications.

Out of scope: payment, SEO, public publishing, ratings, reviews, analytics, AI content generation, moderation UI, Healthcare tenant activation.

## Ownership

- Owning bounded context: Discover.
- Backend domain module: `discover-domain`.
- API adapter: `api-bff` under `/api/provider-registration/**`.
- Migration owner: Discover, with Flyway migration executed by `api-bff`.
- Frontend: `web-discover` provider portal.

## Provider Lifecycle

`DRAFT -> CONTACT_VERIFIED -> PROFILE_INCOMPLETE -> READY_FOR_REVIEW -> SUBMITTED -> UNDER_REVIEW -> CHANGES_REQUESTED -> APPROVED -> PUBLISHED -> SUSPENDED -> ARCHIVED`

Every status change writes `discover_provider_status_history`. Draft field updates update the aggregate and preserve the current status unless submission validation advances it.

## Security

Provider drafts are protected by an opaque onboarding token supplied in `X-Provider-Onboarding-Token`. New applications return the token once; frontend stores it locally for resume. Existing staff and patient authentication behavior is unchanged. Future provider identity can replace this token without changing aggregate ownership.

## Persistence

Structured tables:

- `discover_provider_applications`
- `discover_provider_locations`
- `discover_provider_services`
- `discover_provider_documents`
- `discover_provider_submissions`
- `discover_provider_status_history`

No image bytes, browser URLs, or arbitrary HTML are stored in application rows. Uploaded files use the existing object storage abstraction and store metadata plus storage key.

## API

- `POST /api/provider-registration/providers`
- `GET /api/provider-registration/providers/me`
- `GET /api/provider-registration/providers/{id}`
- `PUT /api/provider-registration/providers/{id}`
- `POST /api/provider-registration/providers/{id}/submit`
- `POST /api/provider-registration/providers/{id}/documents`
- `GET /api/provider-registration/providers/{id}/preview`

## Validation

Each step reports missing fields. Submit is blocked until mandatory account, profile, location, service, document, and terms requirements are met.
