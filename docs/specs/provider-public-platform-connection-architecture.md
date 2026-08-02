# Provider Public Platform Connection Architecture

## Status

Approved for Batch A, Batch B hardening, and Batch C publication lifecycle synchronization.

## Scope

This specification defines the structural foundation for provider discovery, platform-controlled provider linking, and future patient booking handoff across Jeevanam.

In scope:

- public provider identity contracts for doctors, clinics, and hospitals
- platform-owned clinic and doctor-practice link persistence
- source/reference/value-object contracts for public and operational identifiers
- booking-capability and availability-state contracts
- versioned integration event contracts
- reconciliation result and audit foundation
- forward-only database schema for platform-owned link records
- minimal validation tests for the loose-coupling foundation
- Platform Admin provider-connection console and read-only review/listing workflows
- Batch B hardening for booking-reference security, relinking, and lifecycle validation
- Batch C publication lifecycle synchronization from Healthcare/Platform into Discover-owned drafts and published profiles

Out of scope:

- Platform Admin linking console UX
- automatic production identity matching
- provider claiming UI
- booking slot selection or appointment confirmation
- Discover directory redesign
- Care booking UX redesign
- Healthcare operational workflow changes
- cross-tenant patient record sharing
- automatic profile merging
- automatic link activation
- direct cross-application repository coupling
- platform-driven edits to Healthcare or Discover-owned operational data

## Ownership

- Owning bounded context: Platform integration
- Shared contract module: `platform-contracts`
- Persistence module: `platform-provider-integration`
- API adapter: `api-bff` only for minimal admin/test invocation if required
- Migration owner: `api-bff` Flyway, forward-only

## Deployment Boundary

The contracts must remain valid if Discover, Care, Healthcare, and Platform later deploy independently.

Each application may currently live in the same monolith, but new code must not assume:

- direct database access across applications
- shared JPA entities across boundaries
- synchronous runtime calls for publication correctness
- public profile dependence for Healthcare operations
- Healthcare dependence for public-only Discover profiles

## Canonical Contract Types

### Public profile type

- `DOCTOR`
- `CLINIC`
- `HOSPITAL`

### Publication status

- `DRAFT`
- `PENDING_REVIEW`
- `CHANGES_REQUESTED`
- `APPROVED`
- `PUBLISHED`
- `SUSPENDED`
- `UNPUBLISHED`

### Platform connection status

- `NOT_CONNECTED`
- `CONNECTION_PENDING`
- `CONNECTED`
- `DISCONNECTED`
- `DISPUTED`

### Link lifecycle status

- `SUGGESTED`
- `PENDING_VERIFICATION`
- `PROPOSED`
- `APPROVED`
- `LINKED`
- `REJECTED`
- `UNLINKED`
- `DISPUTED`

### Booking capability

- `ONLINE_BOOKING`
- `CALL_TO_BOOK`
- `NOT_AVAILABLE`

Reserved for future compatibility:

- `REQUEST_APPOINTMENT`
- `EXTERNAL_BOOKING`

### Availability state

- `AVAILABLE_TODAY`
- `NEXT_AVAILABLE`
- `NO_SLOTS_IN_RANGE`
- `TEMPORARILY_UNAVAILABLE`
- `UNKNOWN`

### Source system

- `DISCOVER_PROVIDER`
- `HEALTHCARE_CLINIC`
- `HEALTHCARE_DOCTOR`
- `HEALTHCARE_HOSPITAL`
- `PLATFORM_ADMIN`

## Reference Model

### ProviderSourceReference

Source identity for a provider fact or link. This must identify the source system, the source entity reference, and the source revision.

### PublicProviderReference

Discover-owned public identity for a provider and, where needed, a practice location.

### BookingTargetReference

Opaque reference that Care can submit to the booking bridge without learning tenant IDs, user IDs, or other operational persistence details.

Batch B hardening requires this reference to be:

- randomly generated or server-protected with a versioned namespace
- stable through unlink/relink of the same relationship
- validated server-side against the active link record
- non-predictable from public provider, clinic, doctor, or tenant identifiers

Deterministic hashes are only acceptable as internal idempotency aids if the public booking reference itself remains opaque.

## Link Persistence

Platform owns link records and their lifecycle history.

Required records:

- `PublicClinicPlatformLink`
- `PublicDoctorPracticePlatformLink`

Each link record stores:

- stable external references
- link lifecycle status
- booking connection status
- source revision metadata
- match evidence metadata
- timestamps for proposal, approval, linking, and unlinking
- optimistic-lock version

Integrity rules:

- no foreign keys into another application's tables
- no cross-schema joins for correctness
- uniqueness enforced on the local reference combination
- link rows are updated idempotently rather than duplicated
- a blank slug does not make the source ineligible
- slugs remain SEO/routing identifiers only
- unlink/relink reuses the existing link identity and booking reference when the same relationship is reactivated
- link lifecycle status and connection status are separate state machines and invalid combinations must be rejected

### Lifecycle / connection compatibility

Valid examples:

- `LINKED` + `CONNECTED` + `ONLINE_BOOKING`
- `LINKED` + `DISCONNECTED` + `CALL_TO_BOOK`
- `APPROVED` + `NOT_CONNECTED` + `CALL_TO_BOOK`

Invalid examples:

- `REJECTED` + `CONNECTED`
- `UNLINKED` + `ONLINE_BOOKING`
- `SUGGESTED` + exposed booking reference
- any state combination that implies operational booking without an approved/linked record and successful validation

## Field Ownership

Discover-owned:

- public biography and page content
- canonical slug
- public gallery/media presentation
- publication lifecycle
- ratings and reviews

Healthcare-owned:

- tenant identity
- doctor user identity
- operational doctor-clinic association
- schedules and availability
- patient records
- appointments and billing

Platform-owned:

- cross-product link state
- match evidence
- reconciliation metadata
- connection lifecycle
- booking-target resolution

## Event Contracts

Versioned event envelopes must carry:

- eventId
- eventType
- eventVersion
- occurredAt
- producer
- sourceReference
- tenantReference when applicable
- correlationId
- causationId
- payload

Supported event families:

- `HealthcareClinicPublicListingChangedV1`
- `HealthcareDoctorPublicListingChangedV1`
- `HealthcareClinicPublicFactsChangedV1`
- `HealthcareDoctorPublicFactsChangedV1`
- `HealthcareDoctorPracticeChangedV1`
- `HealthcareBookingConfigurationChangedV1`
- `PlatformProviderLinkChangedV1`
- `DiscoverPublicProfilePublishedV1`
- `DiscoverPublicProfileUnpublishedV1`

Authoritative producers:

- Healthcare owns the healthcare public-listing/facts/booking-configuration change events.
- Platform owns `PlatformProviderLinkChangedV1`.
- Discover owns publish/unpublish lifecycle events.

Consumers must not publish an event pretending to be another domain.

Payloads must exclude:

- patient data
- passwords
- private documents
- internal staff notes
- raw persistence graphs

## Batch C Publication Lifecycle Synchronization

Batch C adds a Discover-owned lifecycle boundary for provider public profiles.

Discover remains the owner of:

- public profile drafts
- public profile readiness assessment
- review / submit / approve / publish lifecycle
- Discover-owned media, SEO, slugs, galleries, and public presentation

Healthcare remains the source of truth for operational facts and public-listing consent.

Platform remains the authority for public-to-operational links and reconciliation.

Batch C introduces a Discover-facing lifecycle port conceptually equivalent to:

- create clinic draft
- create doctor draft
- create doctor practice draft
- update projected public facts
- submit draft
- get publication readiness
- get publication status
- unpublish source relationship

Batch C uses the existing outbox/event flow as the durable synchronisation path. Controller-only sync may remain as a compatibility path, but it is not the architectural guarantee.

Lifecycle consumers must be source-aware, revision-aware, idempotent, and preserve Discover-owned fields when Healthcare-projected facts refresh.

## Service Contracts

The foundation must expose ports that can later be implemented as in-process adapters or HTTP clients:

- `DiscoverCatalogPort`
- `HealthcareProviderFactsPort`
- `HealthcareAvailabilityPort`
- `PlatformConnectionPort`

Business services must depend on ports, not on another application's repositories.

Batch B introduces a Platform Admin console that reads through these ports and must not access Discover or Healthcare repositories directly.

## Booking Rules

Booking capability and availability are separate.

Examples:

- a provider may be `ONLINE_BOOKING` while `availabilityState = NO_SLOTS_IN_RANGE`
- a public-only profile may be `CALL_TO_BOOK`
- a linked-but-not-ready practice remains `CALL_TO_BOOK`

## Patient Boundary

Care registration does not create a tenant patient association by itself.

Tenant association is created only by an authorized workflow such as:

- confirmed appointment booking
- tenant registration
- existing tenant workflow

The future booking bridge must accept:

- `accountHolderReference`
- `patientSubjectReference`
- `relationshipToPatient`

## Reconciliation

Reconciliation is a recovery mechanism, not a correctness dependency.

Required result model:

- scope
- examined
- inserted
- updated
- unchanged
- skipped
- conflicted
- failed
- failures[]
- startedAt
- completedAt

Reconciliation must be:

- idempotent
- tenant-aware
- source-aware
- non-destructive by default
- safe to rerun

Batch B console actions may expose reconcile, unlink, relink, approve, reject, and audit views, but must still route through the owning Platform service and preserve immutable audit history.

## Failure Behaviour

- Discover unavailable: Healthcare updates still succeed, outbox or local reconciliation can catch up later.
- Healthcare unavailable: Discover public-only profiles remain searchable, online booking can fall back to call-to-book.
- Platform linking unavailable: existing links continue to resolve, new approvals cannot be completed.
- Care unavailable: public discovery remains available, Healthcare operations continue.

## Data Freshness

Projected/link records must carry:

- sourceRevision
- sourceUpdatedAt
- projectedAt
- connectionRevision

Consumers must be able to distinguish fresh, delayed, stale, and unavailable states.

## Validation

Required tests:

- link records persist external references without cross-domain JPA relations
- public practice to tenant-doctor linking is tenant-scoped
- duplicate active links are rejected
- link state changes create audit records
- booking capability remains separate from availability
- public-only profiles resolve to call-to-book
- blank slugs do not make a source ineligible
- opaque booking references do not expose raw tenant details
- reconciliation is idempotent
- older source revisions do not overwrite newer state
- booking references remain stable through unlink/relink of the same relationship
- invalid link/connection combinations are rejected
- audit-subject fallback namespaces include reference type and source system
- Platform Admin provider-connection console routes are restricted to Platform Admin authority
- BFF validation runs with normal tests, not only `-DskipTests`

## File Ownership Map

- `backend/platform/platform-contracts/src/main/java/com/deepthoughtnet/clinic/platform/contracts/providerintegration/**`
- `backend/platform/platform-provider-integration/src/main/java/com/deepthoughtnet/clinic/platform/providerintegration/**`
- `backend/platform/platform-provider-integration/src/test/java/com/deepthoughtnet/clinic/platform/providerintegration/**`
- `backend/api/api-bff/src/main/resources/db/migration/V138__provider_platform_linking_foundation.sql`
