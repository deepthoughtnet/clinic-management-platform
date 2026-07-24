# api-bff Instructions

`api-bff` is an inbound adapter and application orchestration layer.

Allowed:

- controllers
- API DTOs
- API mappers
- endpoint authorization
- request validation
- application orchestration

Forbidden:

- `@Entity`
- `JpaRepository` or `CrudRepository` declarations
- domain persistence repositories
- persistence implementations
- domain aggregates
- packages named `db`, `entity`, `repository`, or `persistence` for owned business data
- controllers calling repositories directly

Any persistence need must be implemented in the owning domain module.
