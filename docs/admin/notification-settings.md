# Administration Notification Settings

## Overview
Tenant-level Notification Settings provide communication defaults for Clinic and CarePilot automation without changing existing campaign/template logic.

Route: `/admin/notification-settings`
API: `GET/PUT /api/admin/notification-settings`

## Settings Coverage
- Channel defaults: Email, SMS, WhatsApp, In-app
- Reminder toggles:
  - Appointment reminders (24h / 2h)
  - Follow-up reminders
  - Billing reminders
  - Refill reminders
  - Vaccination reminders
  - Lead follow-up reminders
  - Webinar reminders
  - Birthday/wellness
- Quiet hours: timezone + start/end window
- Consent/compliance:
  - requirePatientConsent
  - allowMarketingMessages
  - unsubscribeFooterEnabled
- Rate-limit foundation:
  - maxMessagesPerPatientPerDay

## Channel Behavior
- `defaultChannel` must be one of enabled channels.
- `fallbackChannel` is optional, and if set must be enabled and different from default.
- Reminder execution creation uses requested campaign/template channel first.
- If that channel is disabled by tenant settings, fallback/default is applied if enabled.
- If no channel is enabled, execution creation is skipped safely.

## Quiet Hours
- When enabled, timezone and start/end are required.
- Reminder scheduling is deferred to the next allowed send window.

## Provider Readiness
Response includes:
- `emailReady`
- `smsReady`
- `whatsappReady`
- `warnings[]`

Warnings are advisory and non-blocking (for example, SMS enabled while SMS provider is not configured).

## RBAC
- CLINIC_ADMIN: view/update
- PLATFORM_ADMIN / PLATFORM_TENANT_SUPPORT: view/update (tenant context required)
- AUDITOR: read-only
- RECEPTIONIST: read-only
- DOCTOR/BILLING_USER: denied

## Future Roadmap
- Patient-level consent and opt-out registry
- Per-category channel overrides
- Hard per-patient/day send cap enforcement in execution processing
- AI call preference and call-window controls
