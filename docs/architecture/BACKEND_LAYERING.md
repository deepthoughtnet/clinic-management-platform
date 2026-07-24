# Backend Layering

## API adapter

- controller
- request/response DTO
- authorization
- transport validation
- API mapper

## Application layer

- use-case orchestration
- transaction boundary where appropriate
- coordination across domain services
- no HTTP-specific response model in the domain

## Domain layer

- business model
- invariants
- domain services
- catalog lifecycle and validation

## Persistence adapter

- JPA entities where the repository convention uses them
- Spring Data repositories
- persistence mapping
- database-specific queries

## Infrastructure and platform

- audit
- outbox
- shared security contracts
- cross-cutting technical services

## Dependency rules

- Controllers must not inject repositories.
- Domain code must not import `api` packages.
- Domain services must not return API request/response DTOs.
- `api-bff` must not declare JPA repositories.
- Frontend constants cannot be backend entitlement authority.

## Migration ownership

Flyway files may remain physically under `api-bff` resources if that is the current executable application convention, but each migration must state or clearly imply its owning bounded context.

## Package examples

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/...`
- `backend/domains/<domain>/src/main/java/com/deepthoughtnet/clinic/<domain>/...`
- `backend/domains/<domain>/src/main/java/com/deepthoughtnet/clinic/<domain>/db/...`
- `backend/platform/platform-audit/src/main/java/com/deepthoughtnet/clinic/platform/audit/...`
