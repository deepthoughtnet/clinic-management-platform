# Frontend Architecture

## State and routing

- URL-synchronized tabs are the default pattern for major workspaces.
- The URL is the source of truth for visible workspace state.
- Route state must survive refresh and direct link navigation.

## Data access

- Use typed API clients.
- Backend remains the source of truth for access, entitlement, and validation.

## Security and visibility

- Navigation hiding is UX only.
- Route guards must exist for platform-only and role-restricted areas.
- Feature/module visibility must follow backend-provided entitlement state.

## UX rules

- No browser `alert` or `confirm` APIs.
- Use accessible dialogs for destructive or state-changing actions.
- Do not use UUIDs as primary user-facing labels.
- Provide loading, empty, error, and confirmation states.

## Workflow preservation

- Manual and non-AI workflows must remain usable when AI features are disabled.
- Frontend may hide unavailable entry points, but backend enforcement remains authoritative.
