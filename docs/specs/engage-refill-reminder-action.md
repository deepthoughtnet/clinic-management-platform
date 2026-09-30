# Engage refill reminder and patient refill request

## Scope

Add the existing `REFILL_REMINDER` CarePilot family an authenticated, idempotent
patient refill-request action. The existing CarePilot reminder scheduler and
campaign execution ledger remain authoritative. A request is a pharmacy review
intake record; it is not a sale, dispense, cart, or payment.

## Ownership and placement

- Business owner: `inventory-domain` (pharmacy refill-request lifecycle).
- Persistence: additive refill-request table and repository in `inventory-domain`.
- HTTP adapter: `api-bff` patient-portal DTO/controller orchestration only.
- Engage integration: existing `CarePilotReminderTriggerService`, campaign
  template renderer, and `CampaignExecutionService`; no second scheduler.
- Frontend: existing `web-care` patient prescriptions/refill workspace.
- Migration: executable Flyway location under `api-bff`, owned by inventory.

## Safety boundaries

Requests require an authenticated patient session and tenant/patient ownership,
an existing finalized prescription, and an idempotency key of tenant + patient +
prescription + refill cycle. They remain `REQUESTED` for pharmacy review and do
not create a pharmacy sale or dispense. Voice remains globally disabled.
