# CarePilot Webinar Automation v1

CarePilot Webinar Automation v1 adds webinar/event lifecycle management, attendee operations, reminder automation, and analytics without changing core clinic workflows.

## Lifecycle

Webinar statuses:
- `DRAFT`
- `SCHEDULED`
- `LIVE`
- `COMPLETED`
- `CANCELLED`

Webinar types:
- `HEALTH_AWARENESS`
- `WELLNESS`
- `CLINIC_EVENT`
- `MARKETING`
- `EDUCATIONAL`
- `OTHER`

## Data Model

Tables:
- `carepilot_webinars`
- `carepilot_webinar_registrations`

Registrations support patient-linked and lead-linked attendees.

## Registration Flow

1. Clinic creates webinar.
2. Staff registers attendee (patient or lead linked).
3. Duplicate registration is prevented by tenant + webinar + email.
4. Capacity checks are enforced when configured.
5. Attendance can be marked as attended/no-show/cancelled.

## Reminder Flow

Campaign types added:
- `WEBINAR_CONFIRMATION`
- `WEBINAR_REMINDER`
- `WEBINAR_FOLLOW_UP`

Reminder trigger behavior:
- Uses existing CarePilot scheduler + execution infrastructure.
- Skips cancelled webinars and cancelled registrations.
- Duplicate-safe based on source reference + reminder window + channel.
- Uses configured template channel (`EMAIL`/`SMS`/`WHATSAPP`) from active campaign template.
- Patient-facing send is only created when registration has `patientId`.
- Lead-only registration remains operationally tracked without forced patient send.

## Attendance and Follow-up

Attendance statuses:
- `REGISTERED`
- `CONFIRMED`
- `CANCELLED`
- `NO_SHOW`
- `ATTENDED`

Follow-up scheduling:
- Attended follow-up window: `WEBINAR_ATTENDED_FOLLOWUP`
- No-show follow-up window: `WEBINAR_MISSED_FOLLOWUP`

## Analytics

Summary metrics:
- total webinars
- upcoming webinars
- completed webinars
- total registrations
- attendance count and rate
- no-show count and rate
- webinar conversions (foundation metric)
- registrations by source
- attendee engagement count

## RBAC

- `CLINIC_ADMIN`: full webinar management
- `RECEPTIONIST`: webinar and registration management
- `AUDITOR`: read-only access
- `PLATFORM_ADMIN`: access with tenant context
- `DOCTOR`, `BILLING_USER`: no webinar management permission in v1

## API Endpoints

- `GET /api/carepilot/webinars`
- `GET /api/carepilot/webinars/{id}`
- `POST /api/carepilot/webinars`
- `PUT /api/carepilot/webinars/{id}`
- `POST /api/carepilot/webinars/{id}/status`
- `GET /api/carepilot/webinars/{id}/registrations`
- `POST /api/carepilot/webinars/{id}/register`
- `POST /api/carepilot/webinars/{id}/attendance`
- `GET /api/carepilot/webinars/analytics/summary`

## Future Roadmap

- Zoom/Google Meet/Teams adapters
- Webinar-specific engagement scoring hooks
- Webinar timeline and delivery observability per registration
- Automated lead creation from anonymous registrations
- Advanced funnel automation sequences
