# Provider Public Platform Connection Architecture

## Status

Approved for Batch A, Batch B hardening, Batch C publication lifecycle synchronization, Batch E2A ownership/consent separation, and Batch E2B public profile authoring/publication.

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
- Batch E2A tenant consent separation, provider ownership, membership, claim intent, and dispute foundations
- Batch E2B provider public profile authoring, completeness, preview, moderation, and publication lifecycle

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

## Batch E2A Additions

Batch E2A introduces a clearer separation between:

- Healthcare tenant consent to participate in public discovery
- Discover-owned provider profile ownership and memberships
- Platform-owned cross-application connection approval
- bounded disputes and ownership-transfer foundations

This batch does not remove the legacy public-listing boolean immediately. Existing runtime paths may continue to mirror the legacy field for compatibility, but new browser-facing contracts must prefer the richer consent/ownership summaries and opaque claim references.

### Consent semantics

Healthcare tenant state is consent only:

- `DISABLED`
- `ENABLED`
- `REVOKED`

Consent indicates that the tenant permits a clinic/provider entity to participate in Discover connection workflows. It does not itself grant publication, ownership, or booking capability.

### Provider ownership

Discover owns:

- public profile ownership records
- provider profile memberships
- claim-intent validation after provider OTP authentication

Recommended ownership states:

- `UNCLAIMED`
- `CLAIM_PENDING`
- `VERIFIED`
- `REJECTED`
- `REVOKED`
- `DISPUTED`
- `TRANSFER_PENDING`

Provider workspace projections must derive a separate business work-item status from
the latest ownership lifecycle. A verified ownership record is authoritative over a
stale claim-intent or review status. The initial supported projection states are:

- `CLAIM_SUBMITTED`
- `PLATFORM_REVIEW`
- `OWNERSHIP_VERIFIED`
- `CONSENT_REQUIRED`
- `READY_FOR_PUBLICATION`
- `PUBLISHED`
- `DISPUTED`
- `REJECTED`
- `REVOKED`

The projection may retain legacy claim and review fields for compatibility, but UI
copy and allowed actions must use the derived work-item status. `OWNERSHIP_VERIFIED`
must not display pending-review copy or expose an open-claim action. If tenant
consent remains disabled, the item may remain actionable with `Tenant consent
required` as its reason; consent, publication, platform connection, and booking
capability remain independent states.

### Batch E2A ownership-state cleanup

The provider details page and Healthcare presence card must render from the latest
persisted ownership lifecycle, not from the existence of a claim reference alone.

Required provider page modes:

- `CLAIM_INTENT_CREATED`
- `PROVIDER_AUTHENTICATED`
- `CLAIM_SUBMITTED`
- `CLAIM_PENDING`
- `OWNERSHIP_VERIFIED`
- `CLAIM_REJECTED`
- `CLAIM_DISPUTED`
- `CLAIM_REVOKED`
- `CLAIM_EXPIRED`

Mode selection must be driven by the persisted lifecycle and latest ownership
projection. Unknown or inconsistent states must fall back to a read-only view and
must never expose claim submission controls.

Provider-safe detail responses must include:

- ownership status
- review status
- derived work-item or page mode
- tenant consent status
- publication status
- platform connection status
- booking capability
- submitted timestamp
- reviewed timestamp
- ownership updated timestamp
- claim note as read-only data
- allowed actions

Healthcare presence responses must include:

- ownership status
- current opaque connection reference when available
- ownership updated timestamp
- public profile synchronized timestamp when relevant
- allowed actions

Duplicate claim-intent creation must be rejected when an active claim or verified
ownership already exists, using a lifecycle-specific conflict response code such as
`ownership_already_verified` or `active_claim_exists`.

The lifecycle policy must be authoritative in the discover domain and the API
controllers must consume projected `allowedActions` from the backend instead of
deriving claim actions from status strings locally. Provider workspace and
Healthcare presence responses should prefer the current active lifecycle, not the
presence of historical claim rows alone.

The Healthcare action label must change with the lifecycle:

- `UNCLAIMED`: `Connect a Provider account`
- `CLAIM_PENDING`: `Open Provider Dashboard` or `View pending claim`
- `VERIFIED`: `Open Provider Dashboard` or `View ownership`

Verified ownership must keep the Provider workspace as the canonical place for
public-profile management and must not auto-authenticate Healthcare admins as
provider users.

Recommended membership roles:

- `OWNER`
- `MANAGER`
- `CONTENT_EDITOR`
- `VIEWER`

### Connection lifecycle

Platform owns the connection lifecycle:

- `NOT_CONNECTED`
- `PROPOSED`
- `PENDING_VERIFICATION`
- `APPROVED`
- `LINKED`
- `DISCONNECTED`
- `DISPUTED`
- `REJECTED`

Platform approval and ownership verification are related but separate decisions.

## Batch E2B Additions

Batch E2B uses the existing Discover landing-page/public-profile subsystem as the
provider-facing public profile authoring and publication workflow. It must remain
separate from provider ownership, Healthcare tenant consent, and platform
connection approval.

### Product boundary

Provider public profile authoring must support:

- section-based editing rather than a single long form
- structured profile completeness and actionable missing-item guidance
- preview that reuses the canonical public Discover rendering
- draft publication workflow with immutable published versions
- platform moderation queue for pending, ready, needs-changes, approved, rejected,
  and published states

The provider editor may be a structured landing-page builder as long as it is the
canonical authoring surface for the public profile content.

### Provider-facing lifecycle

Public profile authoring is driven by the latest persisted public-profile lifecycle,
not by ownership or onboarding draft state alone.

Required phases:

- `DRAFT`
- `PROFILE_INCOMPLETE`
- `READY_FOR_REVIEW`
- `SUBMITTED`
- `UNDER_REVIEW`
- `CHANGES_REQUESTED`
- `APPROVED`
- `PUBLISHED`
- `SUSPENDED`

Ownership verification is a prerequisite to editing, but ownership state does not
replace publication state. Tenant consent is independent. Platform connection is
independent. Booking capability is independent.

### Batch E2B.1 draft lifecycle

Batch E2B.1 introduces the first provider-owned public-profile draft workflow
without publication. It is limited to draft creation, section editing, saving,
preview, and deterministic readiness evaluation.

Required draft states:

- `NO_DRAFT`
- `DRAFT`
- `DRAFT_INCOMPLETE`
- `READY_FOR_REVIEW`

Required readiness states:

- `INCOMPLETE`
- `READY`
- `INVALID`

Draft persistence must remain Discover-owned and versioned. The first draft may
be created lazily for a verified Provider owner and must remain private. Draft
creation and save operations must be idempotent and reload-safe.

Batch E2B.1 must not activate submission, moderation, publication, or public
visibility. Tenant consent remains a separate blocker and may disable
submission, but it must not prevent draft editing.

Preferred backend contract shape:

- `discover-domain` owns draft entities, draft version history, readiness
  evaluation, preview projection, and draft lifecycle policy

### Batch E2B.2 moderation and publication

Batch E2B.2 layers maker-checker moderation and explicit publication on top of
the provider-owned draft lifecycle.

Required moderation phases:

- `NOT_SUBMITTED`
- `SUBMITTED`
- `UNDER_REVIEW`
- `CHANGES_REQUESTED`
- `APPROVED`
- `REJECTED`
- `WITHDRAWN`

Required publication phases:

- `UNPUBLISHED`
- `PUBLISHED`
- `SUSPENDED`

Submission is only allowed when the current draft is `READY`, ownership is
`VERIFIED`, tenant consent is enabled, and there is no active dispute or active
submission. The submission command must capture an immutable snapshot of the
current draft version.

Reviewers inspect an immutable submitted snapshot. Provider edits must not
mutate a submitted version. Review decisions are distinct from publication.
Approval does not publish. Publication requires an explicit publish command and
must only act on the approved version.

Tenant consent revocation must remove anonymous visibility safely and
idempotently without deleting draft, submission, review, or publication history.
- `api-bff` exposes provider draft commands/queries and platform inspection
  reads
- `web-discover` owns the provider editor, preview, and readiness UI
- `web-admin` may show read-only draft summary information only

Recommended draft persistence tables:

- `discover_public_profile_drafts`
- `discover_public_profile_draft_versions`

The provider-facing draft workspace should expose:

- Overview
- About
- Contact
- Services
- Specialities
- Facilities
- Timings
- Fees
- Languages
- Media
- SEO
- Preview
- Readiness

Provider action visibility must come from backend `allowedActions` and not from
frontend status inference.

### Completeness

Completeness must be computed from persisted profile content and surfaced as
actionable guidance. The UI should expose missing and optional items rather than a
generic "incomplete" message.

Typical completeness inputs include:

- clinic name / display name
- address / city / state
- description / about content
- at least one speciality
- at least one service
- opening hours / timings
- media assets such as logo, cover image, and gallery
- consultation fees
- languages
- SEO-friendly slug and metadata

The implementation may reuse the existing `publicationReadiness` domain record as
the canonical backend summary if it exposes the same persisted state.

### Preview

Preview must render the same public Discover components used by the public route.
Preview mode may switch between desktop, tablet, and mobile shells, but it must not
introduce editing affordances or a separate public canvas.

### Publication moderation

Platform moderation is a lifecycle state on the public profile publication workflow.
Moderation does not alter ownership, consent, or connection state.

The platform queue should be able to list:

- pending profiles
- ready profiles
- profiles needing changes
- approved profiles
- rejected profiles
- published profiles

Reviewers may approve, reject, or request changes. Publishing creates an immutable
published profile version and does not auto-enable booking or platform connection.

### Discover visibility

Only published profiles may appear in public discovery/search.
Draft, submitted, under-review, and changes-requested profiles must remain hidden
from public search and listing routes.

### Allowed actions

Provider editor allowed actions should be lifecycle-specific and read from the
persisted public profile state. Typical actions:

- save draft
- preview
- submit for review
- view publication status
- open public Discover page when published
- revert to a prior version where supported

Platform admin allowed actions should be lifecycle-specific and read from the same
publication state:

- approve
- reject
- request changes
- view preview and completeness
- open published page after publication

### Ownership and persistence

Ownership remains in Discover provider-ownership tables. Public profile authoring
remains in Discover landing-page/public-profile tables. The API layer may
orchestrate between them, but the storage ownership must not be merged.

### Claim intent

Healthcare may create a short-lived opaque claim/connection reference that the verified provider account resolves after OTP authentication. The reference must be:

- random or server-protected
- expiring
- single-use or explicitly replay-safe
- opaque to the browser
- auditable across creation, authentication, submission, expiry, rejection, and revocation

### Disputes

Disputes are bounded records with statuses such as:

- `OPEN`
- `UNDER_REVIEW`
- `EVIDENCE_REQUESTED`
- `RESOLVED_FOR_CLAIMANT`
- `RESOLVED_FOR_EXISTING_OWNER`
- `CONNECTION_REVOKED`
- `CLOSED`

Disputes must not delete the public profile or merge identities automatically.

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
