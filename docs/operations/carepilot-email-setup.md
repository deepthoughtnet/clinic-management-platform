# CarePilot Email Provider Setup

CarePilot/Engage email uses the `MessageProvider` registry. The configured
value must be a registered provider ID, not a generic transport name.

## Engage provider selection

For the certified MSG91 SMTP adapter:

```dotenv
CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED=true
CLINIC_CAREPILOT_MESSAGING_EMAIL_PROVIDER=msg91-email-smtp
CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS=reminders@notify.deepthoughtnet.com

MSG91_EMAIL_ENABLED=true
MSG91_EMAIL_HOST=smtp.mailer91.com
MSG91_EMAIL_PORT=587
MSG91_EMAIL_USERNAME=<MSG91 SMTP username>
MSG91_EMAIL_PASSWORD=<secret>
MSG91_EMAIL_FROM=reminders@notify.deepthoughtnet.com
```

`msg91-email-smtp` is the provider ID. `smtp` is only a transport/provider
value for the separate legacy notification mail family and must not be used as
the Engage provider selector.

For MSG91 Engage sends, `MSG91_EMAIL_FROM` is authoritative. The optional
`CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS` value should be kept aligned
for operator visibility and legacy compatibility; it does not override the
MSG91 adapter's sender.

## General system mail

The independent notification-domain/provider path uses `clinic.mail.*`:

```dotenv
CLINIC_MAIL_ENABLED=true
CLINIC_MAIL_PROVIDER=smtp
CLINIC_MAIL_HOST=<SMTP host>
CLINIC_MAIL_PORT=587
CLINIC_MAIL_USERNAME=<SMTP username>
CLINIC_MAIL_PASSWORD=<secret>
CLINIC_MAIL_FROM_EMAIL=<from address>
CLINIC_MAIL_FROM_NAME=Jeevanam Healthcare
CLINIC_MAIL_STARTTLS=true
CLINIC_MAIL_AUTH=true
```

Keep this family when non-Engage transactional notifications (for example
invoice/receipt or domain notification emails) still use it. It is not the
Engage provider selector and should not be removed or merged without migrating
those consumers.

## Operational validation

1. Recreate the API container after changing environment variables.
2. Open CarePilot → Messaging.
3. Confirm `Provider: msg91-email-smtp` and `Status: READY`.
4. Use the provider test send only with an approved test recipient.

Readiness is configuration/bean readiness; it does not claim a live provider
delivery unless a test send succeeds.

Never commit SMTP credentials or expose them in status responses/logs.
