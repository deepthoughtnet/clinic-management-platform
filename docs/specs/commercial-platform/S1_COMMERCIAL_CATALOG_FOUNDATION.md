---
spec_id: JCP-S1
title: Commercial Catalog Foundation
status: implemented-pending-uat
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: commercial-domain
api_module: api-bff
frontend_module: web-admin
runtime_cutover: false
destructive_migration_allowed: false
---

# Purpose

Batch S1 introduces the commercial catalog foundation for Jeevanam Healthcare. It establishes catalog records and administrative workflows without changing tenant runtime entitlement behavior.

# Product boundary

- In scope: Jeevanam Healthcare commercial administration
- Out of scope: Jeevanam Discover subscription logic
- Out of scope: Jeevanam Care subscription logic

# Platform Admin-only access

Commercial Catalog administration is visible only to `PLATFORM_ADMIN` users.

# In scope

- commercial capabilities
- commercial modules
- capability-module relationships
- commercial features
- commercial limit definitions
- commercial add-on offers
- add-on relationships to capabilities, modules, features, and limit increments
- idempotent seed/backfill of current runtime-compatible module codes
- audit recording for catalog mutations
- Platform Admin UI for catalog administration

# Explicitly out of scope

- tenant subscription assignment to catalog entities
- plan templates
- immutable plan versions
- tenant-specific overrides
- effective entitlement cutover
- usage metering
- billing and renewals
- Jeevanam Discover subscription flows
- Jeevanam Care subscription flows

# Domain vocabulary

- Capability: a business capability that may be included in a plan or sold as an add-on
- Module: a runtime/navigation module associated with one or more capabilities
- Feature: a finer-grained capability within a module
- Limit definition: a catalog definition for measurable or enforceable limits
- Add-on offer: a catalog record that can later grant capabilities, modules, features, or limit increments

# Domain ownership

`commercial-domain` owns the catalog business rules, persistence entities, repositories, and lifecycle services. `api-bff` owns the REST adapter, DTOs, and transport mapping. `web-admin` owns the administrative UI.

The Flyway migration for this batch is physically hosted under `backend/api/api-bff/src/main/resources/db/migration/` because `api-bff` is the executable migration host in the current reactor, but the catalog schema and business ownership remain in `commercial-domain`.

Dependency direction is `api-bff -> commercial-domain`. `commercial-domain` must not depend on `api-bff`.

# Database model

Batch S1 creates normalized catalog tables:

- `commercial_capabilities`
- `commercial_modules`
- `commercial_capability_modules`
- `commercial_features`
- `commercial_limit_definitions`
- `commercial_addon_offers`
- `commercial_addon_capabilities`
- `commercial_addon_modules`
- `commercial_addon_features`
- `commercial_addon_limit_increments`

The migration is additive and idempotent.

# API contract

Base path: `/api/platform/commercial-catalog`

Endpoints:

- capabilities CRUD and retire
- modules CRUD and retire
- capability-module relationship management
- features CRUD and retire
- limits CRUD and retire
- add-on CRUD and retire
- add-on relationship management

Responses are typed JSON payloads. No raw database errors are exposed.

# Frontend routes and workflows

- `/platform/commercial-catalog?tab=capabilities`
- `/platform/commercial-catalog?tab=modules`
- `/platform/commercial-catalog?tab=features`
- `/platform/commercial-catalog?tab=limits`
- `/platform/commercial-catalog?tab=addons`

The page uses URL-synced tabs, list views, create/edit dialogs, retire confirmation dialogs, and context-aware relationship management dialogs for module/capability/feature/limit association work.

Relationship dialogs must:

- show the parent business name before any secondary code metadata
- expose searchable, grouped selectable lists where appropriate
- show live selected counts
- preserve selections while filtering
- use sticky header/footer layout so actions remain visible
- show relationship counts and previews in the main tables
- confirm discard of unsaved relationship changes through an accessible dialog
- provide explicit save/error feedback
- avoid browser-native alert or confirm dialogs
- avoid UUIDs as primary user-facing labels

# Security

- Backend catalog APIs require `PLATFORM_ADMIN`
- Frontend route visibility is also limited to `PLATFORM_ADMIN`
- Non-platform users receive `403`

# Validation rules

- Codes are uppercase business keys.
- Names are required.
- Duplicate codes are rejected.
- Retired items cannot be newly associated.
- `runtime_module_code` must match known runtime module codes when supplied.
- Features require a valid module.
- Add-on association targets must exist.
- Limit increments must be positive.

# Audit requirements

Audited events include:

- capability created / updated / retired
- module created / updated / retired
- feature created / updated / retired
- limit definition created / updated / retired
- add-on created / updated / retired
- relationship updates

Payloads must not include unsafe data.

# Seed/backfill behaviour

The migration seeds the currently active runtime module codes and conservatively maps them to the initial capability set. Seed data is idempotent.

# Compatibility requirements

- Existing tenant plan/module/subscription enforcement remains authoritative.
- Commercial Catalog does not control tenant runtime access in S1.
- No tenant subscription is assigned to catalog entities in S1.
- Runtime module codes are compatibility references only.

# Acceptance criteria

- Catalog records can be listed, created, updated, and retired by `PLATFORM_ADMIN`.
- Non-platform users cannot access catalog APIs.
- Seed data is present after migration.
- Legacy tenant behavior remains unchanged.
- The new catalog UI is visible only to platform admins.
- Relationship management dialogs are contextual and business-name first.
- Relationship counts are visible in the main tables.
- Relationship previews are available without exposing UUIDs as labels.
- Unsaved relationship edits require accessible discard confirmation.
- Save success and failure states are explicit and non-blocking.
- No browser alert or confirm APIs are used.

# Automated tests

- controller security tests
- migration seed tests
- commercial-domain service tests
- frontend route and UI tests
- architecture guard tests

# Manual UAT scenarios

- verify commercial catalog is visible to platform admins only
- verify create/edit/retire workflows
- verify URL tabs persist on refresh
- verify legacy platform plans page still works
- verify existing tenant navigation and /api/me behavior are unchanged

# Known limitations

- No plan templates or immutable versions yet.
- No tenant assignment to catalog records yet.
- No billing or quota enforcement yet.
- No runtime entitlement cutover yet.

# Batch S2 dependency

Batch S2 depends on this foundation and will add immutable plan templates and versions without replacing the legacy runtime control plane.

# File ownership map

## commercial-domain

- `backend/domains/commercial-domain/src/main/java/com/deepthoughtnet/clinic/commercial/catalog/CommercialCatalogService.java`
- `backend/domains/commercial-domain/src/main/java/com/deepthoughtnet/clinic/commercial/catalog/CommercialCatalogModels.java`
- `backend/domains/commercial-domain/src/main/java/com/deepthoughtnet/clinic/commercial/catalog/CommercialCatalogEnums.java`
- `backend/domains/commercial-domain/src/main/java/com/deepthoughtnet/clinic/commercial/catalog/db/*`
- `backend/domains/commercial-domain/src/test/java/com/deepthoughtnet/clinic/commercial/catalog/CommercialCatalogServiceTest.java`

## api-bff

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog/CommercialCatalogController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog/CommercialCatalogApiService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog/CommercialCatalogDtos.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog/CommercialCatalogControllerSecurityTest.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog/CommercialCatalogMigrationTest.java`

## migration

- `backend/api/api-bff/src/main/resources/db/migration/V117__commercial_catalog_foundation.sql`

## web-admin

- `web-admin/src/pages/platform/CommercialCatalogPage.tsx`
- `web-admin/src/pages/platform/CommercialCatalogRelationshipDialog.tsx`
- `web-admin/src/api/clinicApi.ts`
- `web-admin/src/app/App.tsx`
- `web-admin/src/layout/nav.ts`
- `web-admin/src/layout/TopBar.tsx`
- `web-admin/src/modules/moduleRegistry.ts`

## tests

- backend controller security, migration, and commercial-domain service tests
- frontend commercial catalog UI and route tests

# Architecture decision record

Decision: keep the commercial catalog as a separate bounded context in `commercial-domain`, with `api-bff` acting only as the transport adapter. The catalog is informational in S1 and does not alter live tenant entitlement resolution.
