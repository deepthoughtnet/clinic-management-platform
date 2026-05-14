# Functional Requirements Document (FRD / PRD)

## Scope
Implementation-grounded functional behavior across backend APIs (`/api/**`) and web-admin routes in `App.tsx`.

## Role and Access Matrix (Effective)
- `CLINIC_ADMIN`: full clinic + carepilot + admin operations.
- `RECEPTIONIST`: front-desk + scheduling + lead/webinar + selected finance/pharmacy actions.
- `DOCTOR`: consultation/prescription clinical flow; no lead/webinar/carepilot admin mutations by default.
- `BILLING_USER`: billing/payments/refunds/reports; limited non-clinical.
- `AUDITOR`: read-only operational visibility.
- `PLATFORM_ADMIN` + `PLATFORM_TENANT_SUPPORT`: tenant-scoped platform/admin operations.

## Screen/Module Functional Requirements

### 1. Patients (`/patients`)
- Create, list, search, update, deactivate patient records.
- View linked consultations and prescriptions.
- View patient timeline including documents/consultation/prescription events.

### 2. Appointments (`/appointments`, `/appointments/day-board`, `/queue`)
- Book scheduled and walk-in appointments.
- Update status, priority, reschedule.
- Start consultation from appointment.
- Queue reorder and doctor queue visibility.
- Doctor availability and slot APIs used for booking constraints.

### 3. Consultations (`/consultations`, `/consultations/:id`)
- Create/update/complete/cancel consultation.
- Doctor workspace includes AI-assisted actions (where permitted).

### 4. Prescriptions (`/prescriptions`)
- Create, update, finalize, corrections, print/send, preview/PDF.
- Version history maintained.

### 5. Billing/Finance (`/billing`, `/finance/payments`, `/finance/refunds`)
- Bill CRUD and status transitions (issue/cancel).
- Add/list payments and refunds; bill-level and cross-bill listings.
- Receipt retrieval, PDF, send.
- Invoice email trigger support.

### 6. Inventory + Pharmacy (`/inventory`, `/pharmacy/*`)
- Medicines: list/create/update/activate/deactivate.
- Stock: stock records + transaction history + low stock + expiry alerts.
- Dispensing: queue by prescription, dispense action, bill linkage.

### 7. CarePilot Campaigns (`/carepilot/campaigns`)
- Campaign CRUD, activation/deactivation, runtime inspection.
- Uses campaign types/trigger types/audience types from carepilot domain.

### 8. CarePilot Reminders (`/carepilot/reminders`)
- List reminder executions and detail.
- Retry/resend/cancel/suppress/reschedule actions.
- Reminder scheduling via `CarePilotReminderScheduler`.

### 9. CarePilot Messaging (`/carepilot/messaging`)
- Provider readiness status (email/sms/whatsapp).
- Channel-specific test-send endpoint.
- Delivery webhooks for WhatsApp and generic SMS callbacks.

### 10. CarePilot Leads (`/carepilot/leads`)
- Lead CRUD + filters + pagination.
- Status updates with priority/assignment/follow-up timestamps.
- Activities timeline list and note creation.
- Conversion to patient with optional appointment booking.
- Analytics summary (conversion/follow-up/source/staleness metrics).

### 11. CarePilot Webinar (`/carepilot/webinars`)
- Webinar lifecycle management (draft/scheduled/live/completed/cancelled).
- Registration and attendance operations.
- Reminder/follow-up integration hooks via campaign/reminder infrastructure.

### 12. CarePilot AI Calls (`/carepilot/ai-calls`)
- Campaign CRUD/status + manual/triggered execution creation.
- Execution queue management: retry, cancel, suppress, reschedule.
- Dispatch due executions (manual endpoint + scheduler).
- Webhook ingestion by provider, transcript and event timeline retrieval.
- Scheduler health and analytics summary endpoints.

### 13. Administration
- Templates (`/admin/templates`): centralized template CRUD, activate/deactivate, preview render.
- Notification Settings (`/admin/notification-settings`): channel/reminder defaults + quiet hours + consent toggles.
- Integrations (`/admin/integrations`): readiness summary across messaging/webhook/future providers.
- AI Ops (`/admin/ai-ops`): prompt registry/versioning, invocation logs, usage, guardrails/tool/workflow visibility.

## Workflow and State Transitions

### Lead Lifecycle
`NEW -> CONTACTED -> QUALIFIED -> FOLLOW_UP_REQUIRED -> APPOINTMENT_BOOKED -> CONVERTED` with terminal `LOST` / `SPAM`.

### Webinar Lifecycle
`DRAFT -> SCHEDULED -> LIVE -> COMPLETED` or `CANCELLED`.
Registration status includes `REGISTERED/CONFIRMED/CANCELLED/NO_SHOW/ATTENDED`.

### AI Call Execution Lifecycle
`PENDING -> QUEUED -> DIALING -> IN_PROGRESS -> COMPLETED` with alternate `FAILED/NO_ANSWER/BUSY/CANCELLED/ESCALATED/SKIPPED/SUPPRESSED`.

### Billing Lifecycle
Bill status transitions managed in billing domain (issue/cancel + payment/refund effects).

### Dispensing Lifecycle
Prescription queue -> dispense (full/partial) -> stock movement updates -> optional bill generation.

## Scheduler and Retry Behaviors
- Clinical AI job processing (`clinic.ai.jobs.fixedDelay`).
- Notification reminder scheduler (`clinic.notifications.scheduler.fixedDelay`).
- CarePilot reminders scheduler (`carepilot.reminders.fixed-delay`).
- CarePilot execution scheduler (`clinic.carepilot.scheduler.fixedDelay`).
- AI Calls dispatch scheduler (`carepilot.ai-calls.scheduler.fixed-delay`).
- AI Calls reconciliation scheduler (`carepilot.ai-calls.reconciliation.fixed-delay`).

Retry behavior exists in notification dispatcher, carepilot reminders/executions, and AI calls orchestration with bounded attempts/backoff.

## Error Handling Expectations
- Tenant mismatch returns not found/denied semantics.
- Disabled or unconfigured providers yield controlled statuses (no secret leaks).
- Conversion/booking returns explicit appointment errors where booking fails.
- Webhooks tolerate unmatched IDs/events and continue safely.

