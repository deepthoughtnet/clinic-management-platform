---
spec_id: JCP-S2
title: Plan Templates and Immutable Published Versions
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: commercial-domain
api_module: api-bff
frontend_module: web-admin
runtime_cutover: false
destructive_migration_allowed: false
---

# Purpose

Batch S2 adds commercial plan templates, mutable draft configuration, immutable published plan versions, and a Platform Admin landing area for the commercial subsystem. It extends the commercial catalog foundation without affecting tenant runtime entitlement behavior.

# Product boundary

- In scope: Jeevanam Healthcare commercial administration
- Out of scope: Jeevanam Discover subscription logic
- Out of scope: Jeevanam Care subscription logic

# Platform Admin-only access

Commercial Platform administration is visible only to `PLATFORM_ADMIN` users.

# In scope

- Commercial Platform overview
- plan templates
- mutable draft configuration
- immutable published plan versions
- version history and comparison
- draft validation and publish validation
- archive / retire lifecycle where applicable
- audit-ready lifecycle metadata
- Platform Admin UI for commercial platform administration

# Explicitly out of scope

- tenant subscription assignment
- tenant overrides
- effective entitlement calculation
- runtime entitlement cutover
- usage metering
- quota consumption
- billing
- invoices
- payment collection
- pricing contracts
- migration of legacy tenant subscriptions
- automatic tenant impact from publishing a plan

# Domain vocabulary

- Template: a named commercial plan container that owns drafts and versions
- Draft: the current mutable commercial plan configuration for a template
- Published Version: an immutable snapshot created from a validated draft
- Content hash: a deterministic fingerprint of the published snapshot
- Publication notes: administrator notes recorded at publish time

# Domain ownership

`commercial-domain` owns the template aggregate, draft persistence, version persistence, validation logic, snapshot creation, and comparison logic. `api-bff` owns the REST adapter, DTOs, and transport mapping. `web-admin` owns the overview page, template workspace, and version history UI.

The commercial platform migration is physically hosted under `backend/api/api-bff/src/main/resources/db/migration/` because `api-bff` remains the executable migration host in the current reactor, but the schema and business ownership remain in `commercial-domain`.

Dependency direction is `api-bff -> commercial-domain`. `commercial-domain` must not depend on `api-bff`.

# Database model

Batch S2 creates commercial plan tables for:

- `commercial_plan_templates`
- `commercial_plan_drafts`
- `commercial_plan_versions`

Draft and version storage uses a hybrid model:

- normalized template metadata and lifecycle columns for stable querying
- canonical draft / snapshot JSON for version fidelity and comparison

The migration is additive and does not alter legacy tenant plan, subscription, module, or entitlement tables.

# Lifecycle model

Recommended lifecycle:

- Template starts in `DRAFT`
- Template may become `ACTIVE`
- Template may be retired
- Draft remains mutable and versioned
- Publishing creates an immutable version and leaves the working draft available for subsequent edits

Published versions are immutable snapshots. Corrections require a new draft save and a new published version.

# API contract

Base path: `/api/platform/commercial`

Endpoints:

- overview summary
- plan template CRUD and retire
- draft fetch / save / validate
- publish version
- version history, detail, and comparison

Responses are typed JSON payloads. No raw database errors are exposed.

# Frontend routes and workflows

- `/platform/commercial`
- `/platform/commercial/catalog`
- `/platform/commercial/plans`
- `/platform/commercial/plans/:templateId`
- `/platform/commercial/plans/:templateId/versions/:versionId`

The page uses URL-synced tabs and clearly separates:

- overview
- catalog
- plan templates
- draft configuration
- published versions

Plan workspace behavior:

- business-name-first template creation
- URL-synchronized tabs
- sticky save / validate / publish actions
- dirty-state protection
- accessible discard confirmation
- version history and comparison
- immutable version detail
- no UUIDs as primary user-facing labels
- no browser-native alert or confirm dialogs

Dirty-state protection must remain compatible with the current `BrowserRouter`/`Routes` application architecture. The workspace must not require a data router unless the whole application adopts one. In-page cancel/close actions and browser refresh or tab close are protected; full browser back/forward interception is not required in S2 and should be documented honestly if unavailable.

Plan template creation is identity-first: Name, Code, Description, Target Segment, Display Order, and Status are captured before draft configuration is available. Code may auto-generate from Name before creation, but it becomes immutable after persistence. Configuration, validation, comparison, and publishing are unavailable until the template exists.

Unsaved-change protection must remain compatible with the current `BrowserRouter`/`Routes` architecture. Commercial Plans must not require a data router unless the whole application architecture changes. In-page cancel/close protection and browser refresh/tab-close protection are required; browser back/forward interception is not required in S2 and should be documented honestly if unsupported.

# Security

- Overview, template management, draft management, and version publishing require centralized commercial permissions
- Frontend route visibility is also limited to `PLATFORM_ADMIN`
- Non-platform users receive `403`

# Validation rules

- Codes are uppercase business keys.
- Names are required.
- Duplicate codes are rejected.
- Retired catalog items cannot be newly selected in drafts.
- Features require an included parent module.
- Limit values must match the configured limit value type.
- Published versions cannot be mutated.
- A plan cannot publish with unresolved blocking validation errors.
- Draft edits must use optimistic locking to prevent silent overwrites.
- Validation state is authoritative and lives with the draft response, not in plan-list counters.
- A draft is `NOT_VALIDATED` until an explicit validation run occurs, `STALE` after edits, `INVALID` when blocking findings exist, and `VALID` only when validation is current and blocking-free.
- Empty drafts are never publishable.
- Plan codes are fixed after creation.

# Audit requirements

Audited events include:

- template created / updated / retired
- draft saved / validated
- version published
- version comparison requested, where supported by existing audit patterns

Payloads must not include unsafe data.

# Compatibility requirements

- Existing tenant plan/module/subscription enforcement remains authoritative.
- Commercial Platform does not control tenant runtime access in S2.
- No tenant subscription is assigned to plan templates or published versions in S2.
- Runtime module codes are compatibility references only.
- Legacy `/platform/plans` remains available until a separate migration decision is made.

# Acceptance criteria

- Commercial Platform overview is visible to `PLATFORM_ADMIN`.
- Plan templates can be created, edited, retired, validated, and published.
- A plan template must have persisted business identity before draft configuration is available.
- Name is entered before Code, and Code may auto-generate from Name before creation.
- Code becomes read-only after creation unless a separate controlled rename flow is approved.
- Template creation and initial draft creation are atomic.
- Draft configuration, validation, comparison, and publishing are disabled until a template exists.
- Drafts are mutable and versioned.
- Published versions are immutable.
- No unnamed or unsaved plan template can be published.
- Version history and comparison work without exposing UUIDs as primary labels.
- Non-platform users cannot access commercial platform APIs.
- Legacy tenant runtime behavior remains unchanged.
- The legacy commercial catalog route remains compatible.

# Automated tests

- domain template and version lifecycle tests
- migration tests
- controller security tests
- role-to-permission mapping tests
- frontend route and UI tests
- architecture guard tests

# Manual UAT scenarios

- verify the Commercial Platform overview loads for platform admins
- verify template creation and draft editing
- verify publish creates a new immutable version
- verify compare shows business-friendly diffs
- verify empty drafts report blocking findings and are not publishable
- verify code is read-only after creation
- verify localized timestamps render in the workspace instead of raw ISO strings
- verify legacy `/platform/plans` still works
- verify existing tenant navigation and `/api/me` behavior are unchanged

# Known limitations

- No tenant assignment or runtime entitlement cutover yet
- No billing or quota enforcement yet
- No pricing contracts yet
- No migration from legacy tenant plans yet
- If a stale UAT placeholder template named `NEW_PLAN_TEMPLATE` exists and has no published versions or tenant references, clean it up with an explicit operator-run maintenance step rather than application startup deletion.

# Batch S3 dependency

Batch S3 will consume plan templates and published versions to assign subscriptions to tenants. S2 does not implement any tenant impact.

# File ownership map

## commercial-domain

- `backend/domains/commercial-domain/src/main/java/com/deepthoughtnet/clinic/commercial/platform/*`
- `backend/domains/commercial-domain/src/main/java/com/deepthoughtnet/clinic/commercial/platform/db/*`
- `backend/domains/commercial-domain/src/test/java/com/deepthoughtnet/clinic/commercial/platform/*`

## api-bff

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/commercial/*`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog/*`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/platform/commercial/*`

## migration

- `backend/api/api-bff/src/main/resources/db/migration/V118__commercial_platform_templates_and_versions.sql`

## web-admin

- `web-admin/src/pages/platform/CommercialPlatformPage.tsx`
- `web-admin/src/pages/platform/CommercialPlanTemplatesPage.tsx`
- `web-admin/src/pages/platform/CommercialPlanTemplateWorkspacePage.tsx`
- `web-admin/src/api/clinicApi.ts`
- `web-admin/src/app/App.tsx`
- `web-admin/src/layout/nav.ts`
- `web-admin/src/layout/TopBar.tsx`

## tests

- backend domain, controller, migration, and frontend commercial platform tests

# Architecture decision record

Decision: model commercial plan templates and versions in `commercial-domain` with immutable published snapshots and a mutable working draft, while keeping the legacy tenant entitlement path unchanged. S2 introduces commercial design-time records only and does not alter production tenant access.
