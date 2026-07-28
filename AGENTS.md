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

## Flyway Migration Rules

- Never assign a Flyway migration version from memory, a prompt, an ADR, or a previous task.
- Before creating a new migration, always scan every Flyway migration directory in the repository to determine the current highest migration version.
- Always use the next available unused version.
- Never assume that the latest version belongs to the current module only; check the entire repository.
- Before renaming or modifying an existing migration, verify whether it has already been applied by checking `flyway_schema_history`.
- Never edit the contents of an already-applied migration in a shared environment. Create a new forward-only migration instead.
- Before considering a task complete, verify that there are no duplicate migration versions.
- Validate Flyway, or start the application, after adding a new migration.

Recommended repository scan:

```bash
find . -path "*/src/main/resources/db/migration/V*.sql" | sort
```

Never create a Flyway migration without first determining the latest migration version from the repository.

## Spring Bean Wiring and Integration Rules

Creating a Java class is not a complete implementation until its runtime wiring has been verified.

Verify:

- Correct Spring stereotype annotation (`@Service`, `@Component`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`, or explicit `@Bean`).
- Package is included in Spring component scanning.
- Constructor dependency injection resolves correctly.
- No missing bean, ambiguous bean, or circular dependency issues.
- New REST endpoints are reachable and properly secured.
- Configuration properties are correctly registered.
- Event publishers/listeners are wired when introduced.
- Repository/entity scanning is correct.
- Application context starts successfully.
- Integration or context-load tests cover meaningful backend additions.

> A feature is not complete until the application starts successfully and the complete execution path (Controller -> Service -> Repository -> Database, where applicable) has been verified.

## Backend Feature Completion Checklist

Every backend feature must verify:

- Database migration version verified.
- No duplicate Flyway versions.
- Bean registration verified.
- Component scanning verified.
- Constructor injection verified.
- Endpoint reachable.
- Security configuration updated if required.
- Application starts without bean errors.
- Context/integration tests pass.
- Architecture validation passes.

## Completion definition

A task is incomplete until:

- architecture tests pass
- relevant module tests pass
- affected builds pass
- no forbidden dependency is introduced
- all created/changed files are reported
