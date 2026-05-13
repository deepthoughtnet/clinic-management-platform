# CarePilot Email SMTP Setup

This guide configures real SMTP delivery for CarePilot EMAIL channel.

## CarePilot Provider Flags

Set these values to enable CarePilot email dispatch:

- `CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED=true`
- `CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS=no-reply@clinic.local`

## SMTP Settings (Current Platform Path)

The current notification infrastructure uses `clinic.mail.*` keys.

- `CLINIC_MAIL_ENABLED=true`
- `CLINIC_MAIL_PROVIDER=smtp`
- `CLINIC_MAIL_HOST=smtp.example.com`
- `CLINIC_MAIL_PORT=587`
- `CLINIC_MAIL_USERNAME=...`
- `CLINIC_MAIL_PASSWORD=...`
- `CLINIC_MAIL_STARTTLS=true`
- `CLINIC_MAIL_AUTH=true`

## Spring Mail Compatibility Keys

For environments already standardized on Spring Mail variables, keep these aligned:

- `SPRING_MAIL_HOST=smtp.example.com`
- `SPRING_MAIL_PORT=587`
- `SPRING_MAIL_USERNAME=...`
- `SPRING_MAIL_PASSWORD=...`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`

CarePilot status checks `clinic.mail.host` first and falls back to `spring.mail.host` for readiness visibility.

## Local Testing Options

Safe local SMTP tools:

- MailHog
- Mailpit
- Gmail app password (local testing only)

Never commit real SMTP credentials to source control.

## Operational Validation

1. Open CarePilot -> Messaging.
2. Confirm EMAIL status is `READY`.
3. Send a test message from the Test Send modal.
4. Validate campaign executions show `SENT` with provider metadata in runtime/ops views.
