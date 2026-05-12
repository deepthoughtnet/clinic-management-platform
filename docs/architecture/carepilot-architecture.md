# CarePilot Architecture (Preparation Stage)

## Objective

Prepare CarePilot as a modular product area inside the existing modular monolith without disrupting current clinic workflows.

## Strategy

- Keep current clinic routes and pages stable.
- Introduce lightweight frontend product boundaries first.
- Defer backend domain extraction until feature requirements are finalized.

## Planned CarePilot Domains

- Campaign orchestration
- Messaging and channel policy
- Reminder automation
- Patient engagement analytics
- Leads lifecycle
- Webinar automation
- Voice / AI call workflows
- Ops console insights

## Integration Direction

- CarePilot modules consume existing tenant/auth context.
- Backend integration should use existing RBAC and tenant scoping contracts.
- New capabilities should prefer additive APIs over breaking changes.

## Future Eventing Direction

When operational features are implemented:

- Use outbox-style event publication for campaign and notification side effects.
- Keep idempotency boundaries explicit at command handlers.
- Preserve auditability for patient-facing communication actions.

## Extraction Readiness

This structure supports gradual extraction by isolating product concerns early while retaining a single deployable runtime today.
