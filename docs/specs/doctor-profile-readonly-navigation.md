# Doctor Profile Read-Only Navigation

## Scope

This spec covers doctor identity navigation in the web-admin Doctor Workspace and adjacent operational screens.

## Behavior

- Clicking a doctor name or avatar opens a read-only doctor profile route.
- The read-only profile reuses the existing doctor profile data source.
- Users with edit permission see an `Edit Profile` action on the read-only profile.
- The existing editable doctor details form remains available via the edit route.

## Routing

- Read-only profile: `/doctors/:id/profile`
- Editable details: `/doctors/:id`

## Safety

- No persistence, RBAC, or doctor-profile schema changes are required.
- Name/avatar navigation must not expose the editable form by default.
