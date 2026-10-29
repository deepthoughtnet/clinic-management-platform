# AIVA V2 Voice Pre-Verified Phone Trust — Phase 3A.1

## Scope

Align Voice identity/trust state with the controlled pre-verified-phone policy.
Caller-ID matching remains identification only. Pre-verification is derived only
from a prior successful Voice OTP challenge bound to the same patient, tenant,
and normalized phone, and only when the effective Voice policy permits it.
Voice booking execution, appointment lookup, cancellation, and rescheduling stay
disabled.

## Placement

- `api-bff` owns Voice call identity assessment, policy resolution, OTP evidence
  lookup through the existing OTP application service, and construction of the
  channel-neutral booking execution context.
- `patient-domain` remains owner of patient records; this phase adds no patient
  verification field or persistence.
- Existing Patient Portal OTP challenge persistence remains the evidence source;
  no migration or frontend work is planned.

For a controlled clinic override, configure the existing policy map with
`enabled: true` and `trust-mode: PREVERIFIED_PHONE_ALLOWED` under
`clinic.aiva.voice.transaction-auth.clinic-overrides.<clinic-uuid>`. Tenant
overrides use `tenant-overrides.<tenant-uuid>`. Application default remains
`DISABLED`; do not set the global default to preverified mode.

## Security invariants

- A patient/mobile match alone never authenticates or authorizes a caller.
- `PREVERIFIED_PHONE` requires a prior successful Voice challenge for the same
  tenant, identified patient, current registered mobile, and normalized inbound
  phone, plus an explicit effective policy allowing this mode.
- The evidence is `patient_portal_otp_challenges.verified_at` for a row with
  `channel=VOICE`, `tenant_id`, `identified_patient_id`, and matching
  `phone_normalized`; the patient service also confirms the currently stored
  patient mobile still matches. No separate patient verification flag is added.
- Registered-unverified callers receive no patient-owned capabilities and are
  never duplicated through guest registration.
- OTP_REQUIRED continues to use the exact call-bound challenge flow.
- Voice execution remains hard-disabled.
