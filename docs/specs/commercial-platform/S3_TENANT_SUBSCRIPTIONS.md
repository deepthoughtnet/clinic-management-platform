---
spec_id: JCP-S3
title: Tenant Subscriptions & Assignment
status: implemented-pending-uat
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: commercial-domain
api_module: api-bff
frontend_module: web-admin
runtime_cutover: false
destructive_migration_allowed: false
---

# Tenant Subscriptions & Assignment

## Purpose

Batch S3 introduces commercial subscription records for Jeevanam Healthcare Platform Admins. It allows a Platform Admin to record which published commercial plan version is assigned to a tenant, manage lifecycle status, and review subscription history.

This batch is commercial-record keeping only. It does not change tenant runtime access.

## Product Boundary

This batch applies only to Jeevanam Healthcare commercial administration.

It does not implement subscription logic for Jeevanam Discover or Jeevanam Care.

## Platform Admin-Only Access

All S3 subscription endpoints and UI routes are restricted to `PLATFORM_ADMIN`.

Permissions:

- `commercial.subscriptions.view`
- `commercial.subscriptions.manage`

## In Scope

- Commercial tenant subscription records
- Assignment to a published commercial plan version
- Subscription lifecycle transitions
- Subscription history timeline
- Commercial platform subscription overview KPIs
- Platform Admin UI for subscriptions and assignment
- Audit records for each lifecycle action

## Explicitly Out of Scope

- Tenant runtime entitlement cutover
- `/api/me` changes
- `TenantSubscriptionService` changes
- `ModuleEntitlementInterceptor` changes
- `tenant_plans`, `tenant_subscriptions`, or `tenant_modules` changes
- Usage metering
- Billing
- Invoices
- Payment collection
- Effective entitlement calculation
- Discover or Care subscription logic

## Domain Vocabulary

- Subscription: a commercial assignment record linking a tenant to a published plan version.
- Published version: an immutable published commercial plan version from S2.
- Assignment: creation of a commercial subscription record.
- Lifecycle action: activate, pause, resume, cancel, expire, replace, or schedule.

## Domain Ownership

The commercial domain owns:

- subscription lifecycle rules
- validation
- persistence entities
- repositories
- event history
- audit-ready metadata

The API BFF owns:

- REST controllers
- request/response DTOs
- authorization
- transport validation
- orchestration into the commercial domain

The web-admin owns:

- subscription list and detail workspace
- assignment dialog
- lifecycle action confirmations
- typed API usage

## Database Model

S3 adds additive tables only:

- `commercial_tenant_subscriptions`
- `commercial_subscription_events`

The subscription table stores the tenant, plan template, published version, lifecycle status, effective dates, identifiers, notes, timestamps, and optimistic version.

The event table stores lifecycle history.

No legacy entitlement tables are altered.

## API Contract

Base path:

- `/api/platform/commercial/subscriptions`

Routes:

- `GET /api/platform/commercial/subscriptions`
- `GET /api/platform/commercial/subscriptions/{id}`
- `POST /api/platform/commercial/subscriptions`
- `POST /api/platform/commercial/subscriptions/{id}/activate`
- `POST /api/platform/commercial/subscriptions/{id}/pause`
- `POST /api/platform/commercial/subscriptions/{id}/resume`
- `POST /api/platform/commercial/subscriptions/{id}/cancel`
- `POST /api/platform/commercial/subscriptions/{id}/replace`
- `GET /api/platform/commercial/subscriptions/{id}/history`
- `GET /api/platform/commercial/subscriptions/status-counts`

Validation failures are returned as structured business errors.

## Frontend Routes and Workflows

Routes:

- `/platform/commercial`
- `/platform/commercial/catalog`
- `/platform/commercial/plans`
- `/platform/commercial/subscriptions`
- `/platform/commercial/subscriptions/:subscriptionId`

The Commercial Platform overview now includes subscription KPIs.

The subscriptions page supports:

- assignment dialog
- list filtering
- detail summary
- history timeline
- lifecycle actions with confirmation dialogs

## Security

The backend is authoritative.

Platform Admin access is enforced by controller security. UI route hiding is not relied on for protection.

## Validation Rules

- A subscription requires a tenant, published version, and effective start date.
- A published version must exist and must be published.
- A retired template cannot be assigned.
- A tenant cannot have overlapping active subscriptions.
- Terminal subscriptions cannot be activated again.
- Paused subscriptions can resume only when valid.
- Replacements create a new record and supersede the old one.

## Audit Requirements

Every lifecycle action records:

- audit event
- subscription event history row
- actor
- timestamp
- status transition
- remarks where provided

## Seed/Backfill Behaviour

S3 does not create subscription seed data.

Existing published commercial plans from S2 remain the source of assignable versions.

## Compatibility Requirements

Commercial subscription records do not alter runtime authorization. Tenant access remains governed by the legacy plan and module model until Batch S4.

## Acceptance Criteria

- Platform Admin can create a commercial subscription for a tenant.
- Platform Admin can view subscription summary and timeline.
- Platform Admin can activate, pause, resume, cancel, and replace a subscription.
- Future-start assignments are scheduled.
- Only published versions are assignable.
- Retired templates are not assignable.
- Only one active subscription exists per tenant at a time.
- Every lifecycle action creates history and audit records.
- Overview KPIs reflect backend counts.
- No runtime entitlement behavior changes.

## File Ownership Map

- `backend/domains/commercial-domain/.../commercial/subscription/*`: commercial subscription domain
- `backend/api/api-bff/.../commercial/subscription/*`: subscription API adapter
- `backend/api/api-bff/src/main/resources/db/migration/V119__commercial_tenant_subscriptions.sql`: migration owner for the additive schema
- `web-admin/src/pages/platform/CommercialSubscriptionsPage.tsx`: subscriptions workspace
- `web-admin/src/pages/platform/CommercialSubscriptionAssignmentDialog.tsx`: assignment workflow
- `web-admin/src/api/clinicApi.ts`: typed commercial subscription client
- `web-admin/test/commercial-subscriptions-page.test.mjs`: frontend coverage

## Architecture Decision Record

S3 follows the existing modular-monolith architecture:

- commercial-domain owns business rules and persistence
- api-bff remains an adapter/orchestration layer
- web-admin remains a typed client UI
- runtime entitlement enforcement remains legacy-only until Batch S4
