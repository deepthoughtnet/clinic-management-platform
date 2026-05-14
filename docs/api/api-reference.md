# API Reference (Implementation Inventory)

Authentication: Bearer JWT (Keycloak issuer). Most endpoints are tenant-scoped and require tenant context headers/selection.

## Core Clinic
### Patients (`/api/patients`)
- `GET /api/patients`
- `POST /api/patients`
- `GET /api/patients/{id}`
- `PUT /api/patients/{id}`
- `PATCH /api/patients/{id}/deactivate`
- `GET /api/patients/{patientId}/consultations`
- `GET /api/patients/{patientId}/prescriptions`

### Appointments (`/api/appointments`)
- `GET /api/appointments`
- `POST /api/appointments`
- `POST /api/appointments/walk-in`
- `GET /api/appointments/{id}`
- `PATCH /api/appointments/{id}/status`
- `PATCH /api/appointments/{id}/priority`
- `PATCH /api/appointments/{id}/reschedule`
- `POST /api/appointments/{appointmentId}/start-consultation`
- `GET /api/appointments/today`
- `POST /api/appointments/queue/reorder`

### Doctors (`/api/doctors`)
- `GET /api/doctors/availability`
- `GET /api/doctors/{doctorUserId}/slots`
- `GET /api/doctors/{doctorUserId}/queue/today`
- `POST /api/doctors/{doctorUserId}/availability`
- `PUT /api/doctors/availability/{id}`
- `GET /api/doctors/{doctorUserId}/profile`
- `PUT /api/doctors/{doctorUserId}/profile`

### Consultations (`/api/consultations`)
- `GET /api/consultations`
- `POST /api/consultations`
- `GET /api/consultations/{id}`
- `PUT /api/consultations/{id}`
- `PATCH /api/consultations/{id}/complete`
- `PATCH /api/consultations/{id}/cancel`

### Prescriptions (`/api/prescriptions`)
- list/create/get/update/finalize/preview/corrections/print/mark-sent/send
- `GET /api/prescriptions/{id}/history`
- `GET /api/prescriptions/consultations/{consultationId}`
- `GET /api/prescriptions/{id}/pdf`

### Vaccinations
- `GET /api/vaccines`, `POST /api/vaccines`, `PUT /api/vaccines/{id}`
- `PATCH /api/vaccines/{id}/deactivate`
- `GET /api/vaccinations/due`, `GET /api/vaccinations/overdue`
- `GET/POST /api/patients/{patientId}/vaccinations`

## Finance
### Bills/Payments/Refunds (`/api/bills`)
- bill CRUD + issue/cancel
- `POST /api/bills/{billId}/payments`
- `GET /api/bills/{billId}/payments`
- `GET /api/bills/payments`
- `POST /api/bills/{billId}/refunds`
- `GET /api/bills/{billId}/refunds`
- `GET /api/bills/refunds`
- receipts/pdf/send-invoice-email

### Receipts (`/api/receipts`)
- `GET /api/receipts/{id}`
- `GET /api/receipts/{id}/pdf`
- `POST /api/receipts/{id}/send`

## Pharmacy and Inventory
### Medicines (`/api/medicines`)
- list/get/create/update/activate/deactivate

### Inventory (`/api/inventory`)
- stocks, transactions, low-stock, expired/expiring alerts

### Dispensing (`/api/inventory/dispensing`)
- `GET /queue`
- `GET /{prescriptionId}`
- `POST /{prescriptionId}/dispense`
- `POST /{prescriptionId}/bill`

## CarePilot
### Campaigns (`/api/carepilot/campaigns`)
- list/get/runtime/create/activate/deactivate

### Templates (`/api/carepilot/templates`)
- list/create/patch

### Executions (`/api/carepilot/executions`)
- list/failed/attempts/create/retry/resend

### Reminders (`/api/carepilot/reminders`)
- list/get/retry/resend/cancel/suppress/reschedule

### Ops (`/api/carepilot/ops`)
- failed executions
- execution timeline

### Analytics (`/api/carepilot/analytics`)
- summary

### Engagement (`/api/carepilot/engagement`)
- overview/cohorts/high-risk/inactive

### Messaging (`/api/carepilot/messaging/providers`)
- `GET /status`
- `POST /{channel}/test-send`

### Delivery Webhooks (`/api/carepilot/webhooks`)
- WhatsApp verify/status callbacks
- SMS generic webhook callback

### Leads (`/api/carepilot/leads`)
- list/get/create/update/status
- `GET /{leadId}/activities`
- `POST /{leadId}/notes`
- `POST /{id}/convert`
- `GET /analytics/summary`

### Webinars (`/api/carepilot/webinars`)
- webinar CRUD/status
- registrations list/create/attendance
- analytics summary

### AI Calls (`/api/carepilot/ai-calls`)
- campaigns CRUD/status/trigger/manual-call
- executions list/get/transcript/events
- execution actions: retry/cancel/suppress/reschedule
- `POST /executions/dispatch-due`
- `POST /webhooks/{provider}`
- scheduler health + analytics summary

## AI Platform
### Copilot/Clinical AI (`/api/ai`)
- status
- patient-summary, structure-notes, suggest-diagnosis
- prescription-template suggestions, patient instructions, clinical-summary
- analytics + recent audits

### AI Ops (`/api/ai`)
- prompts + versions + activate/archive
- invocations
- usage summary
- tools
- guardrails
- workflow runs/steps

## Administration
### Templates (`/api/admin/templates`)
- list/get/create/update/activate/deactivate/delete/preview

### Notification Settings (`/api/admin/notification-settings`)
- get/update

### Integrations (`/api/admin/integrations/status`)
- grouped provider readiness summary without secrets

### Doctor Calendar Admin (`/api/admin/doctor-calendars/reconcile`)

## Platform
- `GET/POST/PATCH/PUT /api/platform/tenants...`
- `GET /api/platform/plans`
- tenant user management under `/api/tenant/users`

## Reports (`/api/reports`)
- clinic dashboard, patient visits, doctor consultations, revenue, payment modes, pending dues, vaccinations due, follow-ups, low-stock, prescriptions.

