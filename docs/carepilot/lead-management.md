# CarePilot Lead Management

## Overview
CarePilot Lead Management adds tenant-scoped lead intake, lifecycle management, timeline, follow-up operations, and conversion workflows.

## Lifecycle
Statuses:
- NEW
- CONTACTED
- QUALIFIED
- FOLLOW_UP_REQUIRED
- APPOINTMENT_BOOKED
- CONVERTED
- LOST
- SPAM

Rules:
- `CONVERTED` leads are immutable for core profile/source/pipeline fields; notes/tags remain editable.
- `LOST` and `SPAM` are excluded from active follow-up pipeline.
- Priorities: `LOW`, `MEDIUM`, `HIGH`.

## Source Tracking
Sources:
- WEBSITE
- WALK_IN
- PHONE_CALL
- WHATSAPP
- FACEBOOK
- GOOGLE_ADS
- REFERRAL
- CAMPAIGN
- MANUAL
- OTHER

Leads can store free-form `sourceDetails` and optional `campaignId` linkage.

## Activity Timeline
`carepilot_lead_activities` is append-only and tenant-scoped.

Activity types:
- CREATED
- UPDATED
- STATUS_CHANGED
- NOTE_ADDED
- FOLLOW_UP_SCHEDULED
- FOLLOW_UP_COMPLETED
- CONVERTED_TO_PATIENT
- APPOINTMENT_BOOKED
- CAMPAIGN_LINKED
- LOST
- SPAM_MARKED

Important lifecycle actions write timeline entries (lead create/update/status/note/follow-up scheduling/conversion/appointment link).

## Follow-up Model
Lead fields:
- `lastContactedAt`
- `nextFollowUpAt`

Operational behavior:
- Follow-up schedule/complete events are recorded in timeline.
- Scheduler foundation records operational lead follow-up reminder due entries for active due leads (tenant-scoped, duplicate-safe marker).
- Converted/lost/spam leads are skipped.

## Conversion + Optional Appointment
Endpoint: `POST /api/carepilot/leads/{id}/convert`

Supports:
- convert-only
- convert-and-book-appointment

Flow:
1. Resolve lead by tenant.
2. Duplicate-safe patient lookup by mobile, then email.
3. Create patient through existing `PatientService` only if no duplicate.
4. Mark lead converted and link patient.
5. Optional appointment booking via existing `AppointmentService.createScheduled(...)`.
6. If appointment booking fails, patient conversion remains successful and error is returned explicitly (`appointmentError`).

Links retained on lead:
- `campaignId`
- `convertedPatientId`
- `bookedAppointmentId`

## APIs
Lead management:
- `GET /api/carepilot/leads`
- `GET /api/carepilot/leads/{id}`
- `POST /api/carepilot/leads`
- `PUT /api/carepilot/leads/{id}`
- `POST /api/carepilot/leads/{id}/status`
- `POST /api/carepilot/leads/{id}/convert`

Timeline:
- `GET /api/carepilot/leads/{leadId}/activities`
- `POST /api/carepilot/leads/{leadId}/notes`

Analytics:
- `GET /api/carepilot/leads/analytics/summary`

## Analytics
Summary includes:
- total/new/qualified/converted/lost
- follow-ups due
- follow-ups due today
- overdue follow-ups
- conversion rate
- source breakdown
- stale leads
- high-priority active leads
- conversions with appointment
- average hours to conversion

## RBAC
- CLINIC_ADMIN: full read/write/convert/book appointment
- RECEPTIONIST: create/update/follow-up/convert/book appointment
- AUDITOR: read-only (including timeline)
- PLATFORM_ADMIN: tenant-scoped access only via tenant context
- DOCTOR: no lead mutation permissions
- BILLING_USER: no lead permissions

Frontend hides mutation controls for read-only roles; backend remains source of truth.

## Future Roadmap
Planned additive work:
- inbound channel/webhook lead creation
- deeper staff-targeted reminder delivery channel model
- cohort-driven campaign targeting for lead segments
- richer assignment/routing automation
