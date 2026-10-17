# Jeevanam Production Provider Values To Set

This checklist contains no existing secret values. Existing values in the
production env file were preserved exactly; blank existing keys are called out
separately rather than rewritten.

## Email

Existing blank production keys that require owner-provided values:

- `CLINIC_MAIL_HOST` — approved general SMTP host
- `CLINIC_MAIL_USERNAME` — general SMTP username
- `CLINIC_MAIL_PASSWORD` — general SMTP password
- `CLINIC_MAIL_FROM_EMAIL` — approved general-mail sender
- `CLINIC_MAIL_FROM_NAME` is already a placeholder and requires the approved
  display name

## MSG91 / Engage Email

| Variable | Purpose | Expected format | Current state |
|---|---|---|---|
| `MSG91_EMAIL_USERNAME` | MSG91 SMTP username | Provider-issued username | `__SET_PRODUCTION_SECRET__` |
| `MSG91_EMAIL_PASSWORD` | MSG91 SMTP password | Provider-issued secret | `__SET_PRODUCTION_SECRET__` |
| `MSG91_EMAIL_FROM` | MSG91-approved sender | Verified sender address | `__SET_PRODUCTION_VALUE__` |
| `CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS` | Engage sender metadata | Same approved sender address | `__SET_PRODUCTION_VALUE__` |

Obtain these from the MSG91/provider account owner.

## DotVoice

| Variable | Purpose | Expected format | Current state |
|---|---|---|---|
| `DOTVOICE_API_KEY` | DotVoice authentication | Provider-issued secret | `__SET_PRODUCTION_SECRET__` |
| `DOTVOICE_FROM_NUMBER` | Approved originating number | Provider/DID number | `__SET_PRODUCTION_VALUE__` |
| `DOTVOICE_TRUNK` | Approved trunk identifier | Provider trunk ID | `__SET_PRODUCTION_VALUE__` |
| `DOTVOICE_CALLBACK_BASE_URL` | Provider callback base URL | Public HTTPS URL, if required | `__SET_PRODUCTION_URL__` |
| `DOTVOICE_STREAM_URL` | Approved stream endpoint | Provider-approved WSS/WS URL | `__SET_PRODUCTION_URL__` |

The current implementation marks callback configuration as optional for
origination, but the values must be confirmed before enabling streaming.

## Sarvam LLM

| Variable | Purpose | Expected format | Current state |
|---|---|---|---|
| `SARVAM_LLM_API_KEY` | Sarvam semantic LLM authentication | Provider-issued secret | `CONFIGURED` |

The LLM key is already populated in production and is not included in the
copyable block below. Sarvam speech uses a separate variable:

| Variable | Purpose | Expected format | Current state |
|---|---|---|---|
| `SARVAM_API_KEY` | Sarvam STT/TTS authentication | Provider-issued secret | `__SET_PRODUCTION_SECRET__` |

## Copyable placeholder block

```env
# ===== Jeevanam Production Provider Values =====

# MSG91 / Engage Email
MSG91_EMAIL_USERNAME=__SET_PRODUCTION_SECRET__
MSG91_EMAIL_PASSWORD=__SET_PRODUCTION_SECRET__
MSG91_EMAIL_FROM=__SET_PRODUCTION_VALUE__
CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS=__SET_PRODUCTION_VALUE__

# DotVoice
DOTVOICE_API_KEY=__SET_PRODUCTION_SECRET__
DOTVOICE_FROM_NUMBER=__SET_PRODUCTION_VALUE__
DOTVOICE_TRUNK=__SET_PRODUCTION_VALUE__
DOTVOICE_CALLBACK_BASE_URL=__SET_PRODUCTION_URL__
DOTVOICE_STREAM_URL=__SET_PRODUCTION_URL__

# Sarvam LLM
# Sarvam speech (STT/TTS)
SARVAM_API_KEY=__SET_PRODUCTION_SECRET__
```

The existing blank general SMTP keys must be populated in place; they were
not replaced with placeholders by this task.
