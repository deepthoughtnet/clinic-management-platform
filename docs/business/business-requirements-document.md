# Business Requirements Document (BRD)

## Executive Summary
Clinic Management Platform is a multi-tenant healthcare operations system with CarePilot engagement automation and an extensible AI platform. It supports clinic front-office, clinical workflows, pharmacy, finance, and patient communication operations while preserving tenant isolation and role-governed access.

## Product Vision
- Deliver a unified clinic OS with operational automation.
- Add CarePilot as engagement and outreach layer.
- Add AI platform as reusable infrastructure (not one-off feature code).

## Business Goals
- Reduce appointment no-shows via reminders and AI calls.
- Improve front-desk throughput through queue/day board workflows.
- Increase collection efficiency via payment/refund visibility.
- Improve pharmacy dispensing accuracy and stock traceability.
- Improve patient retention through campaigns/leads/webinars/follow-ups.

## Business Problems Solved
- Fragmented patient/appointment/consultation data.
- Manual reminder execution and poor delivery visibility.
- Inconsistent lead handling and follow-up leakage.
- Weak pharmacy-finance process continuity.
- AI usage without governance/audit/cost visibility.

## Target Users and Personas
- `CLINIC_ADMIN`: operational owner across modules.
- `RECEPTIONIST`: intake, scheduling, lead and webinar operations.
- `DOCTOR`: consultation/prescription/clinical decision support.
- `BILLING_USER`: billing, payments, refunds, reports.
- `PHARMACIST` aliases (`PHARMA`, `PHARMACY`): dispensing/inventory.
- `AUDITOR`: read-only operational and compliance oversight.
- `PLATFORM_ADMIN` and `PLATFORM_TENANT_SUPPORT`: platform supervision with tenant context.

## Multi-tenant SaaS Model
- Tenant-scoped data access via request context tenant ID.
- Tenant/module entitlement checks enforced in API stack.
- Platform roles manage tenants/plans/modules separately from clinic operations.

## Business Scope by Implemented Module
- Core clinic: patients, appointments, consultations, prescriptions, vaccinations.
- Finance: bills, payments, refunds, receipts, invoice emails.
- Pharmacy: medicine master, inventory stock + transactions, dispensing queue and billing linkage.
- CarePilot: campaigns, reminders, messaging providers, execution timeline, analytics, ops console.
- CarePilot Leads: intake, status pipeline, follow-up, activity timeline, conversion to patient + optional appointment booking.
- CarePilot Webinar: webinar lifecycle, registration, attendance, reminder/follow-up hooks.
- CarePilot AI Calls: campaign + execution queue + retries + failover + webhook/event timeline + transcript foundation.
- Admin: templates, notification settings, integrations status, AI Ops.
- AI Platform: prompt registry/versioning, invocation logs, usage summary, guardrails/tool/workflow foundations.

## Operational Goals and KPIs
- Reminder delivery success rate.
- Appointment no-show reduction.
- Lead conversion rate and conversion-with-appointment ratio.
- Webinar attendance and no-show rates.
- Payment collection velocity and refund turnaround.
- AI call completion/escalation/retry rates.
- AI invocation success/latency/cost trends.

## Non-functional Requirements
- Tenant isolation across all domain operations.
- RBAC enforcement on all mutation paths.
- Scheduler safety and retry resilience.
- Provider abstraction (email/sms/whatsapp/voice/AI) without business coupling.
- Operational observability via analytics, execution logs, and event history.

## Compliance and Governance Considerations
- No secret exposure in status APIs.
- Controlled audit/event trails for critical automations.
- AI guardrail foundation for prompt/use-case governance.
- Webhook verification tokens/secrets for external callbacks.

## Scalability and Enterprise Readiness
- Modular domains and provider SPI boundaries.
- Queue-based execution and retry orchestration for CarePilot + AI Calls.
- Incremental expansion path: realtime voice, richer agent workflows, deeper consent and moderation policies.

## Future Direction (Business)
- Realtime AI receptionist and inbound automation.
- Deeper webinar integrations (Zoom/Meet/Teams).
- Predictive engagement cohorts and proactive care outreach.
- Enterprise workflow engine and advanced AI governance lifecycle.
