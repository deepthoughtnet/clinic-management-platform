# Architecture Constitution

Jeevanam is a modular monolith with explicit bounded-context ownership.

## Purpose

This document defines the dependency direction and the ownership rules that keep platform and product evolution additive, auditable, and maintainable.

## Dependency Direction

```text
web-admin
    ↓ HTTP
api-bff
    ↓
domain/application modules
    ↓
platform abstractions and infrastructure
```

## Core Rules

- `api-bff` is not a domain module and is not a persistence owner.
- Backend is the authoritative security and entitlement layer.
- Persistence belongs to the bounded context that owns the business concept.
- Additive migrations are preferred; destructive replacements of active production paths require explicit migration and regression planning.
- Tenant isolation is enforced server-side.
- Auditability is a product requirement, not an implementation detail.
- Frontend URL state is the source of truth for visible workspace state.
- Lifecycle-managed records should preserve immutable history where the product depends on traceability.
- No generic abstraction should be added unless it solves multiple real use cases already present in the repository.

## Separation Of Concerns

- API: request/response handling, validation, authorization, mapping.
- Domain/application: invariants, orchestration, lifecycle rules.
- Persistence: JPA entities, repositories, queries.
- Platform infrastructure: audit, outbox, security SPI, shared technical services.
- Frontend: typed HTTP client, route/state UX, presentation-only gating.

## Migration Policy

- Additive schema changes are the default.
- Existing production paths remain authoritative until a specific migration plan says otherwise.
- New catalog or plan structures must not silently cut over active tenants.

## Tenant Isolation

- Tenant data access must be server-enforced.
- Frontend gating is only UX.
- `/api/me` and route/module enforcement remain part of runtime tenancy behavior.

## Product Boundary

- Healthcare commercial administration is the scope for the commercial catalog work.
- Jeevanam Discover and Jeevanam Care are separate boundaries for subscription logic.
