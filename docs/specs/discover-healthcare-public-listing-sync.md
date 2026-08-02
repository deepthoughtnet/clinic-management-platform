# Discover Healthcare Public Listing Synchronization

## Scope

Synchronize eligible Healthcare clinic and doctor records into the Jeevanam Discover public listing projection.

In scope:

- project active Healthcare clinics into Discover when public listing is enabled
- project active Healthcare doctors into Discover when public listing is enabled and the parent clinic is eligible
- keep public slugs stable and unique
- provide an idempotent repair/backfill path
- remove unpublished/inactive records from public search without deleting audit history
- keep onboarding-published Discover profiles working alongside Healthcare-managed projections

Out of scope:

- changing booking UX
- changing patient authentication or Care flows
- changing clinical, billing, prescription, laboratory, or vaccination workflows
- redesigning Discover pages
- seeding fixed Discover providers

## Ownership

- Source of truth for operational clinic and doctor records: `clinic-domain`
- Public discovery projection and read model: `discover-domain`
- Orchestration, repair endpoint, and admin wiring: `backend/api/api-bff`
- Flyway migration owner: `backend/api/api-bff`

## Architecture Rules

- `api-bff` may orchestrate between Healthcare and Discover domains.
- `discover-domain` must own public projection persistence and read paths.
- `discover-domain` must not depend on `clinic-domain`.
- Public queries must read from the Discover projection only.
- Healthcare records must never be exposed directly from the frontend.

## Publication Eligibility

### Clinic

A Healthcare clinic is eligible when:

- `active = true`
- `publicListingEnabled = true`
- display name is present
- a valid public location exists
- the record is not deleted or suspended

### Doctor

A Healthcare doctor is eligible when:

- `active = true`
- `publicListingEnabled = true`
- parent clinic is eligible
- doctor name is present
- at least one speciality exists
- qualification or equivalent professional information is present
- the record is not deleted or suspended

Online booking eligibility is separate from public visibility.

## Projection Contract

Discover-managed public projections must contain only public-safe fields, including:

- source system
- source reference
- provider type
- public slug
- display name
- public address summary
- city/state
- public contact phone when allowed
- logo and photo references when public-safe
- speciality / qualification / years of experience
- booking capability
- publication status

Private fields such as internal tenant IDs, user IDs, email addresses, registration documents, and operational-only values must not appear in public responses.

## Synchronization Behavior

- synchronization must be transactional where appropriate
- updates must be idempotent
- repeated repair/backfill calls must not create duplicates
- slugs must remain stable once assigned unless a conflict requires a deterministic change
- projection updates must be tenant-aware and auditable
- disabling public listing or inactivating the source must unpublish the projection from public search

## Repair / Backfill

Provide an admin repair path that can:

- reconcile all eligible Healthcare clinics and doctors for a tenant
- create missing projections
- update stale projections
- skip ineligible records
- report inserted, updated, skipped, and failed counts
- preserve existing public identities where possible

The repair path must be safe to run repeatedly.

## Validation

- backend tests for clinic and doctor eligibility
- backend tests for idempotent upsert and unpublish
- backend tests for tenant isolation
- backend tests for public search visibility
- backend tests for stable slug generation
- integration tests for repair/backfill

