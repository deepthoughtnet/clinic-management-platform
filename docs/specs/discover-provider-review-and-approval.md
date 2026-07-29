# Discover Provider Review And Approval

## Scope

Implement the Platform Admin reviewer workflow for Discover provider applications after provider submission.

Lifecycle in scope:

- `SUBMITTED -> UNDER_REVIEW`
- `SUBMITTED|UNDER_REVIEW -> CHANGES_REQUESTED`
- `UNDER_REVIEW -> APPROVED`
- `APPROVED -> PUBLISHED`
- `CHANGES_REQUESTED -> SUBMITTED` through existing provider resubmission

Out of scope:

- Replacing the existing provider onboarding workflow
- Provider profile management after publication
- Public document exposure outside authorized reviewer endpoints
- Any tenant-scoped admin redesign

## Architecture Placement

- Owning domain: `discover-domain`
- API adapter: `backend/api/api-bff`
- Migration owner: Discover onboarding/public profile persistence in Flyway under `api-bff`
- Platform Admin frontend: `web-admin`
- Provider-facing adjustments: `web-discover`

## Constraints

- `api-bff` remains a transport and authorization adapter only.
- Discover domain owns lifecycle rules, review persistence, and publication orchestration.
- Platform Admin routes must work in platform mode without tenant selection.
- Private verification documents remain accessible only through authorized reviewer endpoints.
- Approval and publication are separate domain actions.
- Existing provider onboarding read-only enforcement remains authoritative.

## Backend Plan

1. Split approval from publication in Discover domain.
2. Add explicit publish operation in Discover domain and `api-bff`.
3. Introduce platform review query/read models for:
   - queue listing
   - review detail
   - review history
4. Extend change-request persistence to support structured reviewer feedback items and actor metadata.
5. Extend status-history/audit mapping to include reviewer lifecycle visibility where repository conventions allow.
6. Add platform-scoped reviewer document content access for private review documents.

## Frontend Plan

1. Add Discover governance navigation under existing Platform navigation.
2. Add `/platform/discover/provider-applications` queue page with URL-driven status/filter state.
3. Add `/platform/discover/provider-applications/:applicationReference` review page.
4. Reuse existing dialogs, tabs, history, and confirmation patterns from Platform Admin.
5. Update provider onboarding to surface `CHANGES_REQUESTED` feedback and read-only review banners using the existing status-driven workflow.

## Validation

- Discover domain tests for valid and invalid transitions
- `api-bff` integration tests for platform-mode access, authorization, queue/detail/actions, and publication
- `web-admin` tests for nav, routes, filters, dialogs, and state transitions
- `web-discover` regression tests for review feedback and editability restoration
