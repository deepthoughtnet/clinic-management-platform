# Jeevanam Care Unified Discovery

## Status

Approved for implementation.

## Scope

Add a Care-hosted discovery experience for authenticated patients without changing existing Health, Care network entitlement, Provider publication, or Discover publication behavior.

In scope:

- authenticated Care discovery routes
- My Care Network and public discovery in the Care shell
- Care-hosted public doctor, clinic, hospital, speciality, and service browsing
- Care-hosted public doctor, clinic, hospital, and speciality profile pages
- result deduplication when an explicit linkage already exists
- Care-aware booking action selection
- low-priority fallback link to external Discover when needed for anonymous access
- frontend tests for discovery routing and result rendering

Out of scope:

- Health-derived Care Network entitlement rules
- clinic-switch authorization
- patient portal token format
- private Health doctor booking logic
- availability logic
- Provider onboarding and approval
- Discover publication workflow and moderation
- publicListingEnabled semantics
- patient authentication mode
- OTP/password implementation
- AIVA / CareAI
- referrals
- guardian/dependent model

## Ownership

- Owning frontend area: `web-care`
- Backend source of truth: existing `api-bff` patient portal and public catalog endpoints
- Persistence ownership: none
- Migration owner: none

## Product Rules

- Authenticated patients stay inside Care for normal search, profile viewing, and booking journeys.
- Care Network results remain server-authorized through patient portal endpoints.
- Public discovery results remain read-only and are sourced from the public catalog APIs.
- The same provider may appear in both sources only when an explicit linkage exists.
- No fuzzy deduplication by name is introduced.
- Public profile rendering uses the published public projection only.

## Validation

- Care routes for doctors, clinics, hospitals, specialities, and services render inside `web-care`
- authenticated Care users can browse their authorized clinic network without leaving Care
- public provider and clinic profile pages render inside Care using public catalog data
- published public providers remain discoverable without exposing unpublished data
- care/public duplicates are removed only when explicit linkage is present
- mobile layout remains usable
- public doctor and clinic profile routes issue one request per slug load and terminate into loading, success, not-found, or error without retry loops
