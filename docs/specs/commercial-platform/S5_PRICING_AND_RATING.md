---
spec_id: JCP-S5
title: Commercial Pricing and Rating Foundation
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: commercial-domain
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

S5 adds immutable commercial pricing to the commercial platform. Pricing becomes the frozen commercial layer attached to published plan versions and is used later for quoting, rating, invoicing, and renewals. S5 does not implement usage metering, billing, invoices, or runtime entitlement changes.

# Boundary

- In scope: pricing definition, validation, version freezing, comparison, and plan pricing UX
- In scope: immutable pricing snapshots attached to published plan versions
- Out of scope: usage metering, invoicing, payment collection, billing, renewals, GST, and payment gateways
- Out of scope: runtime entitlement behavior changes
- Out of scope: subscription model changes beyond referencing published versions

# Ownership

`commercial-domain` owns:

- pricing model and persistence
- rating and pricing validation rules
- published pricing snapshot generation
- pricing comparison and history
- publish-time freezing of pricing into immutable version snapshots

`api-bff` owns:

- REST controllers
- DTO mapping
- endpoint authorization
- transport validation
- orchestration through commercial-domain services

`api-bff` must not own pricing entities or repositories.

# Product model

Commercial pricing is attached to published plan versions. The canonical sequence is:

Catalog → Plan Template → Published Version → Pricing → Subscription → Effective Entitlements → Usage Metering → Billing

Subscriptions must reference published versions only. Pricing must not be copied into subscription records.

# Pricing lifecycle

Draft pricing is edited through the plan template workspace. When a plan version is published, pricing is frozen into immutable pricing snapshot rows and version snapshot JSON.

Rules:

- published pricing is immutable
- new pricing requires a new published version
- pricing changes do not mutate existing subscriptions
- pricing history is append-only

# Pricing model

Supported subscription pricing dimensions:

- monthly
- annual
- quarterly
- one-time
- trial

Supported currencies:

- INR
- USD
- EUR

Supported tax models:

- EXCLUSIVE
- INCLUSIVE
- NONE

Pricing rules:

- monetary values must be positive where applicable
- annual price must not exceed monthly price × 12
- trial days must not exceed 365
- missing currency or tax model is blocking
- duplicate addon pricing is blocking
- metered limit pricing requires a unit price

# Persistence model

Additive tables:

- `commercial_plan_pricing`
- `commercial_plan_metered_rates`
- `commercial_plan_addon_pricing`
- `commercial_pricing_history`

Pricing rows are owned by `commercial-domain` and are written only by domain services.

# API

Suggested endpoints:

- `GET /api/platform/commercial/plan-templates/{templateId}/pricing`
- `PUT /api/platform/commercial/plan-templates/{templateId}/pricing`
- `GET /api/platform/commercial/plan-templates/{templateId}/pricing/validation`
- `GET /api/platform/commercial/plan-templates/{templateId}/pricing/compare`

Endpoints must return typed DTOs and not expose entities.

# UI

The plan template workspace gains a `Pricing` tab with:

- subscription pricing
- metered usage pricing
- add-on pricing
- validation findings
- comparison to previous versions

# Validation

Pricing validation must surface:

- missing subscription price
- invalid annual price
- missing currency
- missing tax model
- metered limit without unit price
- included quantity negative
- duplicate add-on pricing

Validation must be deterministic and must not affect runtime entitlement behavior.

# Comparison

Pricing comparison must highlight:

- monthly
- annual
- metered pricing
- add-on pricing
- tax changes

# Security

Platform Admin only.

Suggested permissions:

- `commercial.pricing.view`
- `commercial.pricing.edit`
- `commercial.pricing.publish`

# Acceptance criteria

- pricing is stored in `commercial-domain`
- pricing is frozen on publish
- subscriptions do not copy pricing
- runtime entitlement behavior is unchanged
- commercial runtime remains disabled by default
- pricing validation and comparison are exposed through typed APIs
- frontend exposes a Pricing tab in the plan workspace
- no existing migration is renamed

