# CarePilot Email Provider Resolution Cleanup

## Scope

Make Engage email provider selection explicit and configuration-driven while
preserving the separate general notification mail transport.

## Decisions

- Engage email selects a registered `MessageProvider` ID. The certified MSG91
  provider ID is `msg91-email-smtp`; `smtp` is not a valid Engage provider ID.
- `clinic.mail.*` remains the configuration family for the legacy notification
  provider and non-Engage transactional mail.
- No scheduler, execution ledger, retry, persistence, or provider SPI changes
  are required.
- MSG91 sender configuration is authoritative for the MSG91 Engage provider.

## Planned changes

- Correct local/runtime provider selection to `msg91-email-smtp`.
- Wire the explicit CarePilot selector through all maintained Docker compose
  variants without enabling it by default in production templates.
- Clarify operational documentation and the CarePilot status presentation so
  provider readiness is not confused with legacy SMTP-host readiness.
- Add/retain regression coverage for explicit registry selection and status
  readiness.

## Protected behavior

Legacy `clinic.mail.*` notification delivery, Engage scheduling/execution,
Voice/DotVoice, AIVA/STT/VAD, and patient/tenant authorization remain
unchanged.
