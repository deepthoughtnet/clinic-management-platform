# CarePilot Platform Documentation

## Scope
Implementation across `backend/domains/carepilot-domain`, CarePilot API controllers, and CarePilot frontend pages.

## Modules
- Campaign engine
- Reminder scheduling and execution
- Retry and delivery audit
- Messaging provider abstraction + test send + webhook ingestion
- Engagement analytics/cohorts
- Lead management v1/v2
- Webinar automation v1
- AI Calls foundation + operational hardening
- Ops console and analytics views

## Campaign and Reminder Engine
- Campaign CRUD and lifecycle actions.
- Reminder scheduler generates due reminders from appointment/follow-up/billing/refill contexts.
- Execution statuses tracked with attempts and retry backoff.

## Messaging Providers
- Channel abstraction: Email/SMS/WhatsApp.
- Provider readiness status endpoint.
- Test-send endpoint by channel.
- Delivery webhook endpoints for WhatsApp and SMS generic callbacks.

## Leads
- Full lead CRUD and filterable search.
- Pipeline statuses and follow-up dates.
- Activity timeline (`lead activities` table) with notes and lifecycle events.
- Conversion flow creates/reuses patient and optionally books appointment.
- Analytics include conversion metrics, follow-up due/overdue, staleness, source breakdown.

## Webinars
- Webinar CRUD/status lifecycle.
- Registration list/create and attendance updates.
- Foundation for reminder and follow-up messaging integration.

## AI Calls
- Campaign + execution queue model.
- Scheduler dispatch + reconciliation scheduler.
- Retry/backoff/failover handling.
- Webhook event ingestion.
- Transcript and event timeline.
- Execution operations: retry, cancel, suppress, reschedule.
- Scheduler health and analytics endpoints.

## Ops Console and Analytics
- Failed executions and execution timelines.
- Engagement overview and risk/inactive cohorts.
- CarePilot analytics summary endpoint for high-level KPIs.

## Controls and Safeguards
- Tenant-scoped all operations.
- RBAC role-gated mutation endpoints.
- Quiet-hour and notification-setting integration foundations.
- Provider-not-configured paths handled safely.

