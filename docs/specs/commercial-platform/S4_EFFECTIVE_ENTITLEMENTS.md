---
spec_id: JCP-S4
title: Effective Entitlements and Safe Runtime Cutover
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: commercial-domain
api_module: api-bff
frontend_module: web-admin
runtime_cutover: guarded
destructive_migration_allowed: false
---

# Purpose

Batch S4 introduces domain-owned effective entitlement resolution for commercial subscriptions, immutable plan versions, tenant overrides, and entitlement snapshots. It adds a shadow-capable runtime source without changing tenant authorization while the runtime feature flag remains disabled.

# Product boundary

- In scope: Jeevanam Healthcare commercial administration
- Out of scope: Jeevanam Discover subscription logic
- Out of scope: Jeevanam Care subscription logic

# Default runtime safety

The default configuration is:

`commercial.runtime.enabled=false`

When disabled:

- `/api/me` remains unchanged
- `ModuleEntitlementInterceptor` remains unchanged
- `tenant_plans` remains authoritative
- `tenant_modules` remains authoritative
- legacy module and feature access remain unchanged
- commercial entitlement snapshots may still be generated in shadow mode

When enabled:

- runtime module and feature checks may use effective commercial entitlements
- the cutover is explicit, observable, reversible, and test-backed
- missing or invalid commercial snapshots fail safely instead of silently falling back unless a separate fallback flag is enabled

# In scope

- effective commercial entitlement calculation
- tenant override persistence and lifecycle
- entitlement snapshot persistence and history
- deterministic canonicalization and content hashing
- shadow-mode comparison against legacy runtime access
- runtime entitlement adapter and feature-flag routing
- platform admin UI for effective entitlements, overrides, history, and legacy comparison
- platform admin APIs for snapshot inspection, regeneration, override management, and diagnostics
- audit records for generation, regeneration, mismatch detection, and override lifecycle actions
- migration and backfill support for active commercial tenants

# Explicitly out of scope

- usage metering
- usage-based remaining allowance calculations
- billing and renewals
- migration or deletion of legacy tenant entitlement records
- destructive replacement of legacy runtime enforcement while the flag is disabled
- automatic global runtime cutover

# Domain vocabulary

- Effective entitlement snapshot: a canonical, immutable record of the resolved tenant commercial access state at a point in time
- Tenant override: a tenant-specific commercial policy that enables, disables, or configures a catalog item or add-on
- Shadow compare: a non-authoritative comparison between legacy runtime access and commercial effective entitlements
- Content hash: deterministic fingerprint of the canonical snapshot content
- Source hash: fingerprint of the upstream subscription/version/override inputs used to produce a snapshot

# Domain ownership

`commercial-domain` owns:

- entitlement calculation rules
- override validation and precedence
- snapshot generation and history
- snapshot validation state
- entitlement comparison logic
- entitlement limit lookup
- override and snapshot persistence
- commercial entitlement lifecycle events

`api-bff` owns:

- REST controllers
- request/response DTOs
- endpoint authorization
- transport validation
- orchestration into commercial-domain services
- runtime routing adapter wiring

`api-bff` must not own commercial entitlement persistence.

# Runtime routing

Create a single runtime facade that chooses between:

- legacy tenant entitlement source
- commercial effective entitlement source

Recommended configuration:

- `commercial.runtime.enabled=false`
- `commercial.runtime.shadow-compare.enabled=false`
- `commercial.runtime.fallback-to-legacy.enabled=false`
- optional `commercial.runtime.tenant-allowlist`

Routing behavior:

- disabled flag: legacy remains authoritative
- enabled flag: commercial effective entitlements become authoritative for module and feature checks
- shadow compare: compute commercial effective entitlements, compare with legacy, and log structured differences without changing authorization

# Effective hierarchy

Resolved content is derived from:

1. Active commercial subscription
2. Immutable published plan version
3. Tenant overrides
4. Canonical effective entitlement snapshot

# Effective content

The canonical snapshot should expose:

- capabilities
- modules
- features
- limits
- add-ons
- provenance
- generation metadata
- deterministic content hash

Business identifiers must remain catalog codes and runtime keys, not UUIDs.

# Override model

Supported override targets:

- capability
- module
- feature
- limit
- add-on

Supported operations:

- enable
- disable
- set value
- set unlimited
- set add-on state

Required rules:

- target code must exist in the active catalog
- operation must match the target type
- expired or cancelled overrides are ignored
- contradictory active overrides are rejected or deterministically resolved according to documented precedence
- a published plan version is immutable and cannot be mutated by an override

# Precedence

Recommended precedence:

Base published plan
↓
Included add-on contributions
↓
Tenant override enable / disable
↓
Tenant limit override
↓
Dependency revalidation
↓
Canonical effective snapshot

Rules:

- tenant disable overrides plan enablement
- tenant enable may add an active catalog item
- a feature requires its parent module to be effective
- disabling a module disables dependent features
- add-on state overrides change add-on contribution state
- limit overrides replace the base value
- orphan features are not allowed in the canonical snapshot

# Validation

Before publishing a snapshot, validate:

- active subscription exists
- subscription references a published immutable version
- subscription is effective for the target time
- immutable version snapshot is readable
- catalog items are resolvable
- feature parent modules are effective
- add-on dependencies are satisfied
- limit values are valid for their type
- override targets are valid
- no contradictory active overrides exist
- a canonical snapshot can be generated

If validation fails:

- do not mark the new snapshot CURRENT
- preserve the prior CURRENT snapshot when safe
- persist or return structured findings
- audit the failure

# Snapshot lifecycle

Suggested statuses:

- CURRENT
- SUPERSEDED
- INVALID
- PENDING_REGENERATION

Suggested generation reasons:

- subscription activated
- subscription resumed
- subscription replaced
- subscription cancelled
- subscription expired
- override created
- override updated
- override retired
- manual regenerate
- backfill
- shadow compare

The canonical snapshot must be immutable once persisted.

# Canonicalization and hashing

Requirements:

- sort capabilities, modules, features, and add-ons by stable business code
- sort limits by code
- normalize null and empty values
- normalize numeric values consistently
- use explicit unlimited semantics
- exclude volatile fields such as generation timestamps from the content hash
- hash canonical JSON with a stable algorithm already used in the repository, preferably SHA-256

Same effective content must produce the same content hash.

# Lifecycle integration

Subscription lifecycle:

- activated -> generate CURRENT snapshot
- resumed -> regenerate CURRENT snapshot
- replaced -> generate new snapshot and supersede the previous current snapshot
- paused -> retain diagnostics, do not grant runtime access when commercial runtime is enabled
- cancelled or expired -> supersede current snapshot and resolve as inactive for runtime access when enabled

Override lifecycle:

- create/update/cancel/expire -> regenerate tenant snapshot
- preserve history and previous current snapshot

# API

Suggested endpoints:

- `GET /api/platform/commercial/tenants/{tenantId}/effective-entitlements`
- `POST /api/platform/commercial/tenants/{tenantId}/effective-entitlements/regenerate`
- `GET /api/platform/commercial/tenants/{tenantId}/effective-entitlements/history`
- `GET /api/platform/commercial/tenants/{tenantId}/effective-entitlements/legacy-comparison`
- `GET /api/platform/commercial/tenants/{tenantId}/overrides`
- `POST /api/platform/commercial/tenants/{tenantId}/overrides`
- `PUT /api/platform/commercial/tenants/{tenantId}/overrides/{overrideId}`
- `POST /api/platform/commercial/tenants/{tenantId}/overrides/{overrideId}/activate`
- `POST /api/platform/commercial/tenants/{tenantId}/overrides/{overrideId}/cancel`

The DTOs must be business-name first and must not expose UUIDs as user-facing primary labels.

# Permissions

Add centralized permissions:

- `commercial.entitlements.view`
- `commercial.entitlements.regenerate`
- `commercial.overrides.view`
- `commercial.overrides.manage`
- `commercial.runtime.diagnostics.view`

Recommended Platform Admin mapping:

- `PLATFORM_ADMIN` receives all S4 commercial entitlement permissions

Clinic roles must not gain access to the platform-admin entitlement APIs in S4.

# UI

Add:

- Commercial Platform overview KPI updates
- `/platform/commercial/entitlements`
- tenant selector
- refresh and regenerate actions
- legacy comparison view
- snapshot history
- override create/edit/cancel workflow
- validation findings display

The page must not require a selected clinic tenant.

# Audit and events

Audit events should include:

- effective snapshot generated
- snapshot generation failed
- snapshot superseded
- override created
- override updated
- override activated
- override cancelled
- legacy comparison executed
- runtime source switched, where configuration audit exists

If repository conventions support outbox/domain events, publish corresponding commercial entitlement events transactionally.

# Cache

Recommended behavior:

- cache CURRENT snapshot by tenant
- invalidate after regeneration
- avoid recalculating on every request
- bound staleness with a documented TTL or request-scoped lookup

# Backfill

Provide an explicit backfill path for tenants with active commercial subscriptions.

Suggested flag:

- `commercial.entitlements.backfill.enabled=false`

Backfill must be idempotent and must not run automatically on startup without an explicit flag.

# Rollout strategy

Stage 1:

- generate snapshots only
- `commercial.runtime.enabled=false`
- `commercial.runtime.shadow-compare.enabled=false`

Stage 2:

- shadow comparison only
- `commercial.runtime.enabled=false`
- `commercial.runtime.shadow-compare.enabled=true`

Stage 3:

- tenant allowlist pilot if supported
- commercial runtime enabled only for listed tenants

Stage 4:

- global cutover with runtime flag enabled

# Rollback strategy

Rollback must be configuration only:

- disable `commercial.runtime.enabled`
- keep snapshots and overrides intact for diagnostics
- preserve legacy runtime authorization behavior

# Acceptance criteria

- snapshots can be generated for active commercial tenants
- snapshots are canonical and deterministic
- overrides are persisted and validated
- legacy runtime behavior is unchanged while `commercial.runtime.enabled=false`
- shadow compare does not change access
- runtime cutover is explicit and reversible
- no legacy entitlement records are deleted or migrated
- published versions remain immutable
- no S5 usage metering or S6 billing behavior is introduced
- no existing migration is renamed

# S4.1 UX polish

The Effective Entitlements page must present:

- a business summary for tenant, active subscription, version, snapshot state, runtime source, and generated time
- empty states that explain why no effective data exists
- validation findings with remediation guidance
- provenance grouped as plan, add-on, override, and effective snapshot
- technical identifiers inside a collapsed technical details section

# S4.2 override management

Override management must support:

- draft creation and editing
- submit, withdraw, approve, request changes, activate, cancel, and rollback workflows
- maker-checker review semantics when enabled by policy
- immutable history and revision records
- backend-calculated impact preview
- transactionally regenerated snapshots after lifecycle transitions

# S4.3 runtime diff

The runtime diff dashboard must provide:

- summary KPIs for commercial readiness and mismatch counts
- tenant readiness rows
- side-by-side legacy vs commercial comparison
- snapshot history and generation history
- rollout readiness classification
- diagnostics that do not change authorization while commercial runtime is disabled
