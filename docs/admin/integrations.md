# Administration Integrations v1

## Purpose
Administration Integrations provides a tenant-scoped, secret-safe readiness dashboard for operational integrations used by Clinic and CarePilot.

Route: `/admin/integrations`
API: `GET /api/admin/integrations/status`

## Supported Integrations
- Messaging:
  - Email / SMTP
  - SMS
  - WhatsApp
- Webhooks:
  - WhatsApp webhook verification/signature foundation
  - SMS webhook shared-secret foundation
- Webinar:
  - External webinar URL support
  - Zoom / Google Meet / Teams marked as FUTURE
- AI / Voice:
  - AI orchestration provider detectability
  - Voice calling and STT/TTS marked as FUTURE

## Response Model
Each integration row includes:
- `key`, `name`, `category`
- `status`: `READY | DISABLED | NOT_CONFIGURED | ERROR | FUTURE`
- `enabled`, `configured`
- `providerName`
- `missingConfigurationKeys`
- `safeConfigurationHints`
- `message`
- `lastCheckedAt`

No secret values are returned.

## Environment Variable Guidance
Examples surfaced as safe hints:
- Email:
  - `CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED`
  - `CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS`
  - `SPRING_MAIL_HOST`
  - `SPRING_MAIL_PORT`
- SMS:
  - `CLINIC_CAREPILOT_MESSAGING_SMS_ENABLED`
  - `CLINIC_CAREPILOT_MESSAGING_SMS_PROVIDER`
  - `CLINIC_CAREPILOT_MESSAGING_SMS_API_URL`
- WhatsApp:
  - `CLINIC_CAREPILOT_MESSAGING_WHATSAPP_ENABLED`
  - `CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PROVIDER`
  - `CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PHONE_NUMBER_ID`
  - `CLINIC_CAREPILOT_MESSAGING_WHATSAPP_BUSINESS_ACCOUNT_ID`
- Webhooks:
  - `CLINIC_CAREPILOT_WHATSAPP_WEBHOOK_VERIFY_TOKEN`
  - `CLINIC_CAREPILOT_SMS_WEBHOOK_SECRET`

## Test Actions
- Reuses existing provider test-send endpoint:
  - `POST /api/carepilot/messaging/providers/{channel}/test-send`
- Only available where safe and supported (currently messaging providers).

## Security Notes
- Tenant context required.
- Backend RBAC enforced.
- No secret/token values returned by status endpoint.
- UI only displays missing keys and safe hints.

## RBAC
- `CLINIC_ADMIN`: view + test actions
- `PLATFORM_ADMIN` / `PLATFORM_TENANT_SUPPORT`: view + test (tenant selected)
- `AUDITOR`: read-only
- `RECEPTIONIST`, `DOCTOR`, `BILLING_USER`: denied

## Future Roadmap
- Vendor-specific connection probes
- Retryable webhook diagnostics
- Voice-provider health checks
- Per-tenant integration override policies
