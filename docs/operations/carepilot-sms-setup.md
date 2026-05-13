# CarePilot SMS Provider Setup

This document describes how to enable the CarePilot SMS channel using the vendor-neutral `generic-http` adapter.

## Environment Variables

Set these values in your environment or deployment configuration:

```bash
CLINIC_CAREPILOT_MESSAGING_SMS_ENABLED=true
CLINIC_CAREPILOT_MESSAGING_SMS_PROVIDER=generic-http
CLINIC_CAREPILOT_MESSAGING_SMS_API_URL=https://example-sms-provider/send
CLINIC_CAREPILOT_MESSAGING_SMS_API_KEY=replace-with-provider-key
CLINIC_CAREPILOT_MESSAGING_SMS_FROM_NUMBER=CLINIC
CLINIC_CAREPILOT_MESSAGING_SMS_SENDER_ID=CLINIC
CLINIC_CAREPILOT_MESSAGING_SMS_TIMEOUT_MS=5000
```

Notes:
- `from-number` or `sender-id` must be configured.
- `api-key` is required by the current `generic-http` adapter contract.
- Do not commit secrets into git.

## Provider Contract (`generic-http`)

CarePilot sends a `POST` request to `api-url` with a JSON payload containing:
- `recipient` / `to`
- `message` / `body`
- `from`
- `senderId`

Headers include:
- `Content-Type: application/json`
- `Authorization: Bearer <api-key>`
- `X-API-KEY: <api-key>`

`2xx` responses are treated as success. Non-`2xx` responses are treated as provider errors.

## Local Testing Strategy

Use a local HTTP mock endpoint (for example, WireMock, MockServer, or a simple local API) that:
- returns `202` for success path tests
- returns `4xx/5xx` for failure path tests

Then use CarePilot Messaging test-send:
- `POST /api/carepilot/messaging/providers/SMS/test-send`

## Extending to Twilio / MSG91 / Textlocal

Keep vendor logic in `backend/providers/messaging-sms` and add provider-specific adapters that:
- implement the same provider-neutral `MessageProvider` contract
- map vendor responses into `MessageResult`
- keep secrets out of API responses and logs

Do not place vendor SDK logic in `carepilot-domain`.

