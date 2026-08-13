# Discover Hospital Doctor Association Management

## Status

Proposed implementation spec for DISC-UAT-HOSPITAL-002A / 002B / 002C / 003.

## Scope

Add an explicit hospital ↔ doctor association model for Jeevanam Discover and project that relation into the public hospital profile and provider-side hospital editor.

In scope:

- persist a Discover-owned hospital-doctor association
- manage hospital-doctor associations from the provider-side hospital profile editor
- project `doctors[]` on public hospital detail from the explicit hospital association source
- render a first-class "Doctors at this hospital" section on hospital profile pages
- make "View doctors" land on the hospital profile at the doctors section
- remove clinic-only wording from hospital gallery/copy
- keep hospital-level booking contact-only for now

Out of scope:

- Care booking semantics
- Healthcare appointment/slot generation
- patient identity/login/session flows
- clinic doctor-practice association redesign
- discover doctor-practice association redesign
- tenant membership semantics
- prescription, billing, laboratory, pharmacy, vaccination, consultation

## Ownership

- Hospital association persistence and projection: `discover-domain`
- Provider-side API and orchestration: `backend/api/api-bff`
- Provider-side and public Discover UI: `web-discover`
- Flyway migration owner: `backend/api/api-bff`

## Association Rule

The relationship is explicit:

`Hospital -> Doctor`

Optional future extension:

`Hospital -> Doctor -> Department`

The association must support:

- hospital reference
- doctor reference
- active/inactive lifecycle
- audit timestamps and row version, consistent with existing conventions

The model must not infer doctors from:

- specialty text
- city
- free text search
- clinic membership
- name matching

The model must not replace Healthcare doctor identity, tenant membership, or clinic membership.

## Provider-side Management

The hospital profile editor must expose a "Doctors / Medical Team" section.

Providers must be able to:

- search/select real public doctors
- view current associated doctors
- add a doctor association
- remove/deactivate a doctor association

Duplicate associations must be prevented.
Inactive/unpublished/non-doctor records must not be associable.

## Public Projection

Hospital detail responses must expose the explicit association-backed doctor list.

The hospital profile must render:

- "Doctors at this hospital"
- associated doctor cards or equivalent reusable doctor views

The hospital profile must remain contact-first:

- no hospital-level Care handoff
- no hospital-level online booking promotion

## Navigation Rule

"View doctors" from a hospital card must land on the hospital detail page and scroll to the doctors section, using a stable anchor such as `#doctors`.

## Validation

- backend tests for association lifecycle, duplicate prevention, and projection
- frontend tests for provider-side hospital editing, public hospital doctors section, and `#doctors` navigation
- build verification for affected frontend/backend modules
