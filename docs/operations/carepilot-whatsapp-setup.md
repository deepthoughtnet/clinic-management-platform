# CarePilot WhatsApp Setup (Meta Cloud API)

This guide configures the CarePilot `WHATSAPP` channel with Meta WhatsApp Cloud API.

## Required environment variables

```bash
CLINIC_CAREPILOT_MESSAGING_WHATSAPP_ENABLED=true
CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PROVIDER=meta-cloud-api
CLINIC_CAREPILOT_MESSAGING_WHATSAPP_API_URL=https://graph.facebook.com/v18.0/<phone-number-id>/messages
CLINIC_CAREPILOT_MESSAGING_WHATSAPP_ACCESS_TOKEN=<meta-access-token>
CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PHONE_NUMBER_ID=<phone-number-id>
CLINIC_CAREPILOT_MESSAGING_WHATSAPP_BUSINESS_ACCOUNT_ID=<business-account-id>
CLINIC_CAREPILOT_MESSAGING_WHATSAPP_TIMEOUT_MS=5000
```

## Provider readiness states

- `DISABLED`: `CLINIC_CAREPILOT_MESSAGING_WHATSAPP_ENABLED=false`
- `NOT_CONFIGURED`: enabled but one or more required keys are missing
- `READY`: provider is `meta-cloud-api` and required keys are present
- `ERROR`: no concrete provider implementation is available in registry

## Test send

Use CarePilot Messaging page test-send for channel `WHATSAPP`:
- recipient must be a valid phone number format
- body is required
- subject is ignored

If provider config is incomplete, test-send returns `NOT_CONFIGURED` safely.

## Local testing guidance

- Use a Meta Cloud API test phone number first.
- Start with low timeout values (for example `5000`) and raise only if needed.
- Confirm outbound firewall access to `graph.facebook.com` from your environment.

## Security guidance

- Never commit access tokens to git.
- Never log request `Authorization` headers.
- Do not expose token values in API responses or UI.
- Rotate access tokens periodically and after any suspected leak.

## Future extensions

Current implementation supports plain text messages only.

Planned extension points:
- template messages
- media messages
- interactive/button payloads
- webhook-based delivery state synchronization
