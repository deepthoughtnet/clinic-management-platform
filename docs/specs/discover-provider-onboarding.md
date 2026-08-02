# Discover Provider Onboarding Platform

## Status

Approved for Phase 3 implementation foundation.

## Scope

Jeevanam Discover owns provider onboarding for individual doctors, clinics, and hospitals before Healthcare tenant activation. This phase supports draft creation, resume, progressive completion, structured provider data, document upload, preview, submission, lifecycle history, and backend events/placeholders for notifications.

Provider workspace scope includes:

- `/provider`
- `/provider/applications/{businessReference}`
- lifecycle-aware application attention cards
- multiple application/profile ownership under one provider account
- discard of incomplete, unsubmitted onboarding drafts
- compact, human-readable application detail presentation
- a shared post-submission status experience for doctor, clinic, and hospital applications

Out of scope: payment, SEO, public publishing, ratings, reviews, analytics, AI content generation, moderation UI, Healthcare tenant activation.

## Ownership

- Owning bounded context: Discover.
- Backend domain module: `discover-domain`.
- API adapter: `api-bff` under `/api/provider-registration/**`.
- Migration owner: Discover, with Flyway migration executed by `api-bff`.
- Frontend: `web-discover` provider portal.

## Provider Lifecycle

`DRAFT -> CONTACT_VERIFIED -> PROFILE_INCOMPLETE -> READY_FOR_REVIEW -> SUBMITTED -> UNDER_REVIEW -> CHANGES_REQUESTED -> APPROVED -> PUBLISHED -> DISCARDED -> SUSPENDED -> ARCHIVED`

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

## Service Codes

Provider service selections use the Discover reference catalog as the canonical source of truth.

- Active selectable service codes are the `SERVICE` catalog rows from the discover reference catalog.
- Provider onboarding persists canonical service codes, not UI labels.
- Service display names are resolved from reference data at save time.
- Historical legacy service codes may be normalized through migration, but new selections must use the active catalog codes.
- Unsupported or inactive service selections are rejected by domain validation before persistence.

## API

- `POST /api/provider-registration/providers`
- `GET /api/provider-registration/providers/me`
- `GET /api/provider-registration/providers/{id}`
- `PUT /api/provider-registration/providers/{id}`
- `POST /api/provider-registration/providers/{id}/submit`
- `POST /api/provider-registration/providers/{id}/documents`
- `GET /api/provider-registration/providers/{id}/preview`

## Public Profile Rendering

- The provider onboarding preview route is a provider-only wrapper around the canonical Discover public provider profile presentation.
- Published public doctor, clinic, and hospital detail routes should reuse the same public profile presentation component, without preview-only banners or provider workflow controls.
- Preview-specific actions such as refresh, readiness guidance, and return-to-editing remain outside the canonical patient-facing profile canvas.
- Directory cards should summarize real published data and link to the canonical public profile route.

## Provider Workspace And Session Restoration

- The provider workspace route (`/provider`) is the canonical signed-in entry point for Discover providers.
- The workspace presents account summary, application status, published profiles, attention items, and recent activity using business-facing labels only.
- Provider workspace reads are authenticated by the Discover provider session only. They must not depend on onboarding draft tokens.
- Successful provider login establishes an opaque server-side provider session and an HttpOnly cookie.
- Provider workspace bootstrap must restore a valid provider session before rendering signed-out UI or redirecting to the provider login page.
- Refreshing `/provider` or reopening the browser while the session remains valid must restore the same provider account without requiring a new OTP challenge.
- Logout and switch-account actions must invalidate the active provider session and clear provider-scoped frontend state before returning to the provider login flow.
- Provider session restoration must not change patient, platform-admin, or public Discover authentication behavior.

## Validation

Each step reports missing fields. Submit is blocked until mandatory account, profile, location, service, document, and terms requirements are met.

## Workspace Behavior

- The provider workspace must calculate a single canonical attention result used by both dashboard KPI and attention cards.
- An incomplete application requires attention when completion is below 100%, required items are missing, the lifecycle is `DRAFT`, `CONTACT_VERIFIED`, `IN_PROGRESS`, or `CHANGES_REQUESTED`, or a reviewer/action is pending.
- Completed or published applications must not appear in the incomplete-attention list.
- A provider account may own multiple applications and published profiles. The workspace must show active applications and published profiles separately.
- `Continue registration` resumes an existing application at its current or earliest incomplete step.
- `Add another profile` starts a separate application intentionally when product rules allow it.
- Discarded applications leave active onboarding, remain auditable, and cannot be resumed unless a restore feature is introduced later.

## Shared Post-Submission Experience

- After submission, provider onboarding must switch from editable wizard framing to a status-oriented application experience.
- The status experience must be driven by the persisted application lifecycle and submitted snapshot/version, not by local UI state or completion percentage alone.
- The shared status experience must support doctor, clinic, and hospital applications using shared components and shared lifecycle logic.
- When an application is `SUBMITTED` or later, the provider dashboard is the default landing page and submit actions are hidden or disabled.
- `CHANGES_REQUESTED` applications remain editable, but only through an explicit edit action surfaced from the status experience.
- Submitted previews must use the submitted snapshot/version, while published profiles must link to the canonical public profile route.

## Requirement Labels

Requirement codes must render with stable business labels. Unknown codes must be humanized to readable text rather than surfaced verbatim.
