# Security Architecture

## Identity and Authentication
- OAuth2 Resource Server with JWT issuer (Keycloak realm).
- Login flow in web-admin uses auth context and redirects to `/login` when unauthenticated.

## Authorization and RBAC
- `@PreAuthorize` checks at controller methods.
- Role-permission model in platform security mappings and permission checker.
- Admin/tenant support intersections used for sensitive platform-tenant operations.

## Tenant Isolation
- Tenant ID obtained from request context holder.
- Services use tenant-scoped lookups; cross-tenant records are not returned.
- Platform endpoints separated under `/api/platform/*`.

## Secret Handling
- Provider status APIs expose readiness flags and missing key names, not secret values.
- Messaging and webhook secrets are injected via env vars in application config.

## Webhook Security
- CarePilot webhook controllers support token/secret verification patterns for WhatsApp/SMS.
- AI call webhook endpoint accepts provider key and payload; tenant-scoped processing is enforced.

## AI Security and Guardrails
- AI guardrail foundation includes profile metadata and pre-execution validation hooks.
- AI invocation logs avoid secret persistence and focus on metadata/redacted summaries.

## Operational Protections
- Retry caps/backoff prevent endless execution loops.
- Suppress/cancel/reschedule controls available for queued automations.
- Quiet hours and notification settings foundation protects off-hours messaging behaviors.
- AI calls include throttling/rate constraints via scheduler/orchestration config.

## Auditability
- Outbox/audit/idempotency platform modules included.
- CarePilot execution attempts/events and AI invocation logs provide traceability.

