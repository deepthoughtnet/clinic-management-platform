# Doctor Mobile Validation and Profile Fallback

## Scope

This spec covers two doctor-workspace mobile behaviors:

1. User creation and editing must only accept Indian mobile numbers with at most 10 digits.
2. Doctor profile hydration must prefer the saved doctor profile mobile and fall back to the tenant user mobile when no profile mobile is stored.

## Ownership

- UI: `web-admin`
- Doctor profile API: `api-bff`
- Shared validation helpers: `frontend/packages/form-validation-kit`

## Behavior

- The Users & Roles mobile field must sanitize user input to digits only and cap it at 10 digits.
- The Doctor Details mobile field must apply the same input behavior.
- The doctor profile response must expose `profile.mobile ?? tenantUser.mobile` so a newly created doctor is prepopulated from the tenant user record when no profile mobile exists yet.
- Existing saved doctor profile mobile values continue to win over tenant-user mobile.

## Safety

- Backend validation remains authoritative.
- No schema migration is required.
- Existing doctor profile save behavior remains unchanged except for the read-time fallback.
