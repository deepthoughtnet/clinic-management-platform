# User Manual

## 1. Login and Tenant Context
1. Open the application and sign in.
2. Ensure a tenant is selected/available for tenant-scoped modules.
3. If session errors appear, use Retry or Clear Session actions.

## 2. Navigation Overview
- Operations: dashboard, appointments/day board, queue, doctor availability.
- Clinical: patients, consultations, prescriptions, vaccinations.
- Pharmacy: inventory, dispensing, stock movements, medicine master.
- Finance: billing, payments, refunds, reports.
- CarePilot: campaigns, reminders, messaging, leads, webinars, AI calls, analytics, ops.
- Administration: clinic profile, users/roles, templates, notification settings, integrations, AI Ops.

## 3. Patients
- Create patient from Patients page.
- Open patient detail to view consultations/prescriptions and timeline.

## 4. Appointments and Queue
- Book scheduled or walk-in appointments.
- Use Day Board for operational triage.
- Reorder queue when required.

## 5. Consultations and Prescriptions
- Doctor opens consultation workspace.
- Record notes/vitals/assessment.
- Create/finalize prescriptions and print/send.

## 6. Billing, Payments, Refunds
- Create/issue bill.
- Add payments from bill detail or Payments page.
- Issue refunds from bill detail or Refunds page.
- Send invoice email / receipt where enabled.

## 7. Pharmacy Operations
- Medicine Master: maintain medicine catalog and active status.
- Stock Movements: review transaction history and adjustments.
- Dispensing: process prescription queue and create linked bill when needed.

## 8. CarePilot Campaigns and Messaging
- Create/activate campaign.
- Monitor executions and failures.
- Check provider status and run test sends.

## 9. Leads
- Capture lead.
- Update status/owner/follow-up.
- Add notes and review timeline.
- Convert lead to patient, optionally book appointment.

## 10. Webinars
- Create webinar and set schedule.
- Register attendees (lead or patient).
- Mark attendance and monitor outcomes.

## 11. AI Calls
- Create AI call campaign or trigger manual call.
- Track execution status, retries, and escalations.
- Open transcript and event timeline for each execution.

## 12. AI Ops
- Review invocation usage/cost summary.
- Manage prompt definitions and versions.
- Inspect guardrails, tools, and workflow run logs.

## 13. Templates / Notification Settings / Integrations
- Templates: centralized reusable message templates with preview.
- Notification Settings: channel defaults, reminder toggles, quiet hours, consent controls.
- Integrations: readiness statuses for messaging/webhooks and future provider placeholders.

## 14. Troubleshooting
- If action buttons are missing, verify role permissions.
- If messages fail, check Integrations and Messaging provider status.
- If reminders/calls do not trigger, verify scheduler and module settings.

## 15. FAQ (Short)
- Why cannot I edit in admin pages? -> Role is read-only.
- Why are cross-tenant records not visible? -> Tenant isolation is enforced.
- Why is provider unavailable? -> Missing env config or provider disabled.

