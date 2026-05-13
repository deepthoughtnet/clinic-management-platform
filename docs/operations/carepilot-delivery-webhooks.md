# CarePilot Delivery Webhooks v1

This guide describes provider-driven delivery lifecycle sync for CarePilot.

## Endpoints

- `GET /api/carepilot/webhooks/whatsapp/meta`
- `POST /api/carepilot/webhooks/whatsapp/meta`
- `POST /api/carepilot/webhooks/sms/generic`

These endpoints are public (`permitAll`) and must be protected using provider verification or shared secrets.

## WhatsApp (Meta Cloud API)

### Verification challenge

Meta calls:

- `hub.mode=subscribe`
- `hub.verify_token=<token>`
- `hub.challenge=<challenge>`

Set:

```bash
CLINIC_CAREPILOT_WHATSAPP_WEBHOOK_VERIFY_TOKEN=<verify-token>
```

If token matches, CarePilot returns `hub.challenge`.

### Optional signature validation

Set:

```bash
CLINIC_CAREPILOT_WHATSAPP_APP_SECRET=<meta-app-secret>
```

When configured, `X-Hub-Signature-256` is validated for incoming POST callbacks.

### Status mapping

- `sent -> SENT`
- `delivered -> DELIVERED`
- `read -> READ`
- `failed -> FAILED`

## SMS Generic Webhook

Endpoint:

- `POST /api/carepilot/webhooks/sms/generic`

Expected fields (vendor-neutral):

- `providerMessageId` (or `messageId` / `id`)
- `status`
- `timestamp` (epoch seconds, optional)
- `providerName` (optional)

### Shared secret

Set:

```bash
CLINIC_CAREPILOT_SMS_WEBHOOK_SECRET=<shared-secret>
```

If configured, request header `X-CarePilot-Webhook-Secret` must match.

### Status mapping

- `queued -> QUEUED`
- `sent -> SENT`
- `delivered -> DELIVERED`
- `read -> READ`
- `failed -> FAILED`
- `bounced -> BOUNCED`
- `undelivered -> UNDELIVERED`
- unknown values -> `UNKNOWN`

## Persistence and audit

Webhook events are stored in `carepilot_delivery_events`.

- matched events update execution `deliveryStatus`
- unmatched events are still persisted (without execution reference)
- payload is redacted/truncated before storage
- secrets are never returned in API responses

## Local testing

- Use Postman/curl to POST sample payloads to webhook endpoints.
- For WhatsApp challenge, call GET with query params.
- For SMS webhook secret mode, include `X-CarePilot-Webhook-Secret`.

## Limitations (v1)

- No webhook-driven retry scheduling changes (send-level retries remain unchanged).
- No provider-specific template/media delivery callbacks yet.
- No webhook-driven delivery reconciliation for email provider yet.
