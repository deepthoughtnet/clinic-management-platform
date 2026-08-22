# Administration Platform Navigation Reorganization

## Status

Approved for implementation.

## Scope

- Tenant-facing Clinic Admin keeps business administration pages.
- Platform Admin owns technical, runtime, provider, AI, and diagnostic operations pages.
- Existing tenant URLs may remain as redirects for compatibility, but they must not bypass authorization.

## Current classification

Tenant-facing business administration pages remain in Clinic Admin:

- Clinic Profile
- Users & Roles
- Templates
- Notification Settings
- Notification Operations

Platform-only technical pages move into Platform Admin:

- Integrations
- AI Ops
- AI Reasoning Console
- Platform Ops
- Realtime AI
- Voice Test

## Expected grouping

Recommended Platform Admin sections:

- Platform Administration
- Provider & Integrations
- AI Operations
- Platform Operations
- Commercial Administration

## Route strategy

Use a stable `/platform/...` route family for platform-only tools.

Compatibility redirects may remain for:

- `/admin/integrations`
- `/admin/ai-ops`
- `/admin/ai-reasoning-console`
- `/admin/platform-ops`
- `/admin/realtime-ai`
- `/ai/voice-test`

Redirects must preserve authorization and must not expose platform-only pages to Clinic Admin roles.

## Authorization rules

- Menu visibility is UX only.
- Frontend route guards must require the platform admin role for platform-only tools.
- Backend authorization must also require the platform admin role for platform-only APIs.
- Tenant-scoped business pages must keep existing tenant authorization and audit behavior.

## Shared API boundary notes

- Clinical reasoning generation remains shared with clinical workflows.
- Prompt registry, prompt versions, invocation logs, tool registry, guardrails, workflow runs, platform integrations, platform ops, realtime AI diagnostics, and voice test are platform-managed surfaces.
- Notification Settings and Notification Operations remain tenant-facing.

## Validation

- Clinic Admin must not see platform-only navigation entries.
- Platform Admin must see the reorganized grouped platform navigation.
- Direct access to old or new platform-only routes must be denied for non-platform roles.
- Existing tenant-facing business admin pages must remain accessible and unchanged in behavior.
