# Release Readiness Notes

## Backend hardening

- Spring Boot actuator endpoints are exposed for `health`, `info`, `metrics`, and `prometheus`.
- Health probes are enabled for readiness/liveness checks.
- API error responses remain normalized through the global exception handler.
- Server error responses avoid exposing exception messages or stack traces.

## Operational behavior

- Keep tenant isolation, RBAC, and audit logging intact during rollout.
- Avoid `flyway repair` for routine local recovery.
- Use a database reset when local migration state is partially applied or corrupted.

## Validation

- Backend: `mvn clean install`
- Frontend: `cd web-admin && npm run build`

