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

### Authoritative projection reuse

Clinic-facing discovery presence views must consume the same persisted platform-link and publication projections used by Platform Admin.

Clinic Admin may humanize the backend status values, but it must not independently infer connection or booking capability from clinic consent, doctor availability, or slug state.

The authoritative source for:

- connection status
- link lifecycle
- booking capability
- ownership status
- publication status

is the synchronized provider-connection projection and its linked Discover/publication records.

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

### Batch E2C.1 publication moderation and publish workflow

Batch E2C.1 makes the Platform Admin moderation workspace authoritative for the
publication lifecycle. It does not introduce any Clinic Admin profile-approval
step. The responsibilities remain:

- Healthcare Clinic Admin controls only tenant Discover consent.
- Provider owns and edits the public profile draft.
- Platform Admin owns moderation review, approval, publication, and unpublish.

Approval and publication are separate commands. Approval marks an immutable
submission as accepted for publication; it does not make the public profile
visible. Publication creates the public projection from the approved submission
and only succeeds when ownership remains `VERIFIED`, tenant consent remains
`ENABLED`, and the approved submission is still valid.

If tenant consent is later disabled while a profile is published, the approved
publication history remains intact but effective visibility becomes hidden until
tenant consent is restored and the current policy allows visibility to resume.
The chosen visibility rule is:

- published profile + consent disabled => `HIDDEN_BY_TENANT_CONSENT`
- published profile + ownership not verified => `HIDDEN_BY_OWNERSHIP`
- unpublished profile => `NOT_PUBLISHED`
- published profile + all gates satisfied => `VISIBLE`

#### State transitions

| State | Allowed next states |
| --- | --- |
| `DRAFT` | `SUBMITTED` |
| `SUBMITTED` | `UNDER_REVIEW`, `CHANGES_REQUESTED`, `REJECTED` |
| `UNDER_REVIEW` | `CHANGES_REQUESTED`, `REJECTED`, `APPROVED` |
| `CHANGES_REQUESTED` | `SUBMITTED` through a new resubmission |
| `REJECTED` | `SUBMITTED` through a new submission |
| `APPROVED` | `PUBLISHED` |
| `PUBLISHED` | `UNPUBLISHED` |
| `UNPUBLISHED` | `PUBLISHED` only if the approved submission remains valid |

The Platform Admin queue should expose submitted, under-review, changes-requested,
approved, published, rejected, and unpublished rows using backend-authoritative
`allowedActions`.

#### Approved submission projection replacement

Publication treats the approved immutable submission as the source of truth for
the mutable Discover public and search projection. An existing projection for the
same provider/profile must be updated, its canonical slug must be reused, and a
new immutable public-projection history row must be appended when the requested
submission version number is already occupied by different historical projection
content. The projection history version and source submission version are separate
identities; a collision in the former must not reject the latter.

Repeated publication of the same approved submission remains idempotent. A current
publication for an older approved submission is superseded without deletion before
the new current publication record is created, preserving publication audit history.
Submission content, moderation decisions, review findings, and request-changes and
approval history are never mutated by publication.

Conflicts remain valid only when a slug or projection is owned by another provider,
or when persistence detects an optimistic-locking/concurrent version conflict.

Compatibility and rollback:

- no schema migration or backfill is required
- existing public slugs and historical projection/publication rows are preserved
- rollback restores the previous service behavior without transforming stored data
- regression coverage must prove that a different-content projection-version
  collision appends history, refreshes the aggregate/search projection, and leaves
  the approved submission unchanged

### Batch E2C.3 publication lifecycle consistency repair

The publication lifecycle record is authoritative for moderation, review, and
provider-workspace publication state. The public provider profile is its public
catalogue/search projection. A successful publish transaction must update both
models atomically; review APIs must never infer workflow state from the catalogue.

Publishing an approved immutable submission performs these operations in one
transaction:

- reuse or update the public projection owned by the same provider/profile and
  retain its canonical slug
- reuse an already-current publication for the same approved submission, or
  supersede an older current publication and append a new current publication
- persist `PUBLISHED`, `published_at`, and the publishing actor on the lifecycle
  records without changing moderation approval or immutable submission content
- expose `UNPUBLISH_PROFILE`, remove `PUBLISH_PROFILE`, and make moderation queue
  counters and provider workspace counters consume the resulting lifecycle state

Repeated publication of the same approved submission is an idempotent success. It
must also reconcile a legacy state where the current publication and catalogue
projection are already `PUBLISHED` but the approved submission's lifecycle fields
remain stale. That reconciliation may update only publication lifecycle metadata;
it must not rewrite content, ownership/readiness/media snapshots, moderation
decisions, findings, or historical publication rows.

Application startup first reprojects every authoritative current `PUBLISHED`
publication from its referenced approved immutable submission. Only then may the
legacy Healthcare clinic synchronizer run. Once the active projection version was
produced by `PROVIDER_PUBLIC_PROFILE_DRAFT`, lower-authority legacy Healthcare
snapshots cannot repoint the aggregate or slug alias to an older projection. This
source-precedence rule is idempotent and preserves all projection history.

The published projection must be self-sufficient for anonymous media and schedule
reads. Publication copies the immutable submission's selected logo, cover, ordered
gallery references, immutable storage keys, content types, filenames/alt text,
weekly timing intervals, and timezone into the versioned public snapshot. Public
media routes resolve membership and storage only from the aggregate's current
published projection; they never query the mutable draft or use provider/reviewer
media endpoints. A media reference absent from that projection is not public.

Existing canonical anonymous routes (`/api/public/clinics/{slug}/logo`, `/cover`,
and `/gallery/{index}`) are the public media contract. Reconciliation may append
one corrected projection-history row when an older Version 20 projection omitted
published media or timing fields. Subsequent reconciliation and publication must
reuse that semantically equivalent projection and create no additional history.

Unpublishing updates the current publication and its source approved submission
to `UNPUBLISHED` while retaining all audit rows. Current-publication selection is
defined by `public_profile_reference`, `current_flag = true`, and optimistic
locking. Submitted version, projection version, creation time, and historical
approved submissions must not override that current record.

Compatibility, backfill, and rollback:

- migration V145 adds publication actor audit metadata and reconciles submission
  lifecycle fields only where a current `PUBLISHED` publication already points to
  that submission
- the data repair is set-based and idempotent; it creates no submissions,
  publications, profiles, projection versions, or slug aliases
- historical publication and projection rows remain unchanged
- rollback is application-only; the nullable audit column and reconciled lifecycle
  metadata are safe to retain

### Batch E2C.3.1 review workspace UX and immutable snapshot completeness

Batch E2C.3.1 improves the provider-facing review-status experience and the
Platform Admin moderation workspace without changing lifecycle transitions,
approval policy, publication policy, ownership policy, consent semantics, or
booking behavior.

In scope:

- provider review-status route for active submissions
- backend provider-facing review action labels derived from submission status
- provider-facing immutable submitted snapshot preview
- reviewer assignment persistence and refresh after `Start Review`
- Platform Admin moderation workspace layout and compact queue rows
- immutable review preview rendering through the structured landing-page renderer
- submitted timings preservation from draft snapshot through review rendering

Out of scope:

- lifecycle transition changes
- publication policy changes
- approval/rejection policy changes
- ownership or consent semantics changes
- booking or connection semantics changes
- rewriting the immutable snapshot schema

### Placement and ownership

- Owning bounded context: `discover-domain`
- API adapter: `backend/api/api-bff`
- Provider-facing browser app: `web-discover`
- Platform Admin browser app: `web-admin`
- Persistence ownership: `discover-domain`
- Migration owner: none unless a forward-only schema change becomes necessary

### Compatibility plan

- The provider workspace action `VIEW_REVIEW_STATUS` must navigate to the new
  review-status route instead of the editable draft preview.
- The provider locked-draft route may continue to exist, but it must no longer
  present the generic draft-load error when a submission is under review.
- The Platform Admin public-profile reviews workspace must remain on the same
  base route, but the rendered layout must separate moderation from the generic
  link-inspection workspace.
- Existing moderation history, findings, and immutable submitted snapshot data
  remain authoritative.

### Validation

- provider review-status route opens from the workspace action
- locked draft no longer shows the generic draft-load error during active review
- provider review page shows the immutable submission version, moderation
  status, reviewer history, findings, and backend-driven allowed actions
- Platform Admin review queue remains compact without horizontal scrolling
- reviewer assignment persists after review start and refreshes in the UI
- submitted timings appear in the immutable review preview
- selected link panel is not shown on the public-profile reviews tab
- provider review preview and platform review preview render the same immutable
  snapshot, including structured timings, media, and section content

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

## E2C.4 Platform Entity Linking Completion

Status: approved for implementation and live UAT on 2026-08-06.

### Authoritative aggregate and cardinality

`PublicClinicPlatformLink` and `PublicDoctorPracticePlatformLink` remain the
Platform-owned connection aggregates. Discover publication and ownership are
eligibility inputs only; neither is a connection state.

For clinic links the current cardinality is exclusive one-to-one while a link
is active: one public clinic may have at most one active operational clinic and
one operational clinic may have at most one active public clinic. Historical
rows for different pairs remain available after rejection, disconnection, or
supersession. The database enforces both active uniqueness predicates.

The canonical clinic target reference is the operational `clinic_profiles.id`.
The tenant reference remains separate. Tenant codes, slugs, and raw UUID
equality are not identity-match evidence.

### Lifecycle and actions

The canonical progression is:

- `PROPOSED` + `CONNECTION_PENDING`
- `APPROVED` + `NOT_CONNECTED`
- `LINKED` + `CONNECTED`

`APPROVED` is verified but is not active. `LINKED` is the only active business
state. Repeated propose, verify, activate, and reconciliation requests with the
same pair and revision return the existing aggregate without duplicate rows or
duplicate transition audit events. A stale source or target revision fails with
the platform business error `stale_match_revision`. Invalid or conflicting
targets fail with a stable business error before persistence.

Backend `allowedActions` is authoritative for Platform Admin rendering. The
frontend must not independently infer proposal, verification, activation,
rejection, suspension, or disconnection eligibility.

### Immutable match evidence

Proposal evidence is calculated server-side from the reviewed public projection
and operational clinic facts. It records normalized display name, normalized
phone, email, city, area/address, canonical slug, and registration/reference
where each fact is actually available. Missing facts are marked missing and
differing facts are retained; public UUID versus clinic slug is never treated as
registration evidence. Verification and activation retain the proposal evidence
and cannot change the selected target.

### Capability derivation

Connection and booking capability remain independent. The BFF evaluates the
operational clinic through Healthcare-owned services and passes the resulting
capability into the Platform aggregate:

- inactive tenant or clinic: `NOT_AVAILABLE`
- active clinic with a published phone but incomplete online-booking setup:
  `CALL_TO_BOOK`
- `ONLINE_BOOKING` only when appointments are enabled and an active, publicly
  listed doctor has active availability

Proposal and verification never expose a platform booking target. Activation
stores the evaluated capability. Reconciliation reevaluates it idempotently.
Public detail/listing may overlay only the current active aggregate capability;
it must not expose link references, tenant references, reviewer identity, or
match evidence.

### Persistence and audit

The forward migration following repository-wide version 145 adds active-link
uniqueness and explicit proposal, verification, activation, suspension, and
disconnection actor/timestamp metadata. Existing audit rows remain immutable.
Each real state transition and capability change records previous state, new
state, actor, note, evidence revision, result, and correlation reference where
available. Safe retries do not create a second transition event.

### Projection consistency

Platform Admin Public Profiles, Suggestions, Links, counters, Clinic Admin
Discover Presence, Provider Workspace, and anonymous public capability all
resolve the same active Platform aggregate. Publication remains Discover-owned:
disconnecting never unpublishes, and unpublishing never deletes link history.
Startup reconciliation may refresh capability but must not fabricate links or
replace reviewed evidence.
