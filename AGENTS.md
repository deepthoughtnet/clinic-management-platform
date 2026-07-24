# Jeevanam Repository Engineering Constitution

## Mandatory reading before implementation

Every coding task must first read:

- `/AGENTS.md`
- every applicable nested `AGENTS.md`
- `/docs/architecture/ARCHITECTURE_CONSTITUTION.md`
- `/docs/architecture/MODULE_BOUNDARIES.md`
- `/docs/architecture/BACKEND_LAYERING.md`
- `/docs/architecture/FRONTEND_ARCHITECTURE.md`
- the approved specification under `/docs/specs/`

## Mandatory implementation workflow

For every non-trivial batch:

1. Inspect the existing implementation.
2. Identify the owning bounded context.
3. Read applicable architecture documents.
4. Create or update an approved feature specification.
5. Produce a proposed file-placement and dependency plan.
6. Validate the plan against module boundaries.
7. Implement.
8. Run architecture tests.
9. Run functional and regression tests.
10. Report deviations and validation results.

## No coding before placement analysis

For substantial features, Codex must first state:

- owning domain
- API module
- persistence module
- migration owner
- frontend area
- allowed dependencies
- planned files

## API BFF rules

`api-bff` is an inbound adapter and application orchestration layer.

Allowed:

- REST controllers
- request/response DTOs
- endpoint authorization
- transport validation
- API mapping
- orchestration through domain/application services

Forbidden:

- JPA entities
- Spring Data repositories
- domain aggregates
- persistence implementations
- packages named `db`, `entity`, `persistence`, or `repository` for domain-owned data

## Persistence ownership

Persistence belongs to the bounded context that owns the business concept.

Migration execution location does not determine domain ownership.

## Compatibility

Active runtime paths must not be destructively replaced without:

- an approved migration specification
- compatibility plan
- backfill plan
- rollback considerations
- regression tests

## Completion definition

A task is incomplete until:

- architecture tests pass
- relevant module tests pass
- affected builds pass
- no forbidden dependency is introduced
- all created/changed files are reported
