# AIVA V2 Voice Authentication Policy — Phase 2A.2

## Scope

Resolve configurable Voice authentication policy by clinic, tenant, then application defaults. This phase controls whether a consent-gated registered-patient OTP challenge may start and which operation requires authentication. Transaction execution, patient-owned lookup, cancellation, rescheduling, and guest booking remain hard-disabled.

## Placement and dependencies

- Policy model, Spring configuration binding, resolver, and Voice integration live in `backend/api/api-bff`, alongside the existing Voice orchestration and Phase 2A.1 authentication adapter.
- Configuration uses narrowly scoped application properties with per-tenant and per-clinic override maps. No generic tenant configuration resolver currently supports this security policy; unrelated notification settings and feature flags are not reused.
- The effective scope precedence is clinic override, tenant override, application default, applied per field. Tenant and clinic identifiers come only from trusted routed Voice context.
- No persistence, frontend, appointment-domain, authentication-provider, or migration changes are planned.

## Security contract

- Safe application defaults: policy disabled, authentication mode `DISABLED`, 15-minute grant TTL, and authentication required for all four operations.
- Supported modes are only `OTP_REQUIRED` and `DISABLED`. A disabled mode never means authentication bypass.
- OTP challenges use the existing consent, exact challenge binding, provider-neutral delivery, tokenless verification, and transcript-redaction path from Phase 2A.1.
- Grant TTL is clamped to 5–30 minutes. Call/session termination continues to clear the grant regardless of remaining TTL.
- Test bypass is not implemented; conversation input cannot alter policy.
- A separate immutable hard-disable remains in force for transaction execution and patient-owned appointment lookup, including after OTP verification.

## Configuration

Spring property prefix: `clinic.aiva.voice.transaction-auth`.

`default-policy` defines application defaults; `tenant-overrides` and `clinic-overrides` are maps keyed by canonical UUID strings. Override fields may be omitted and inherit the next lower-priority value. No Admin UI is added in this phase.

Example:

```yaml
clinic:
  aiva:
    voice:
      transaction-auth:
        default-policy:
          enabled: false
          authentication-mode: DISABLED
          auth-ttl-minutes: 15
          booking-auth-required: true
          lookup-auth-required: true
          cancellation-auth-required: true
          reschedule-auth-required: true
        tenant-overrides:
          "<tenant-uuid>":
            enabled: true
            authentication-mode: OTP_REQUIRED
        clinic-overrides:
          "<clinic-uuid>":
            auth-ttl-minutes: 20
```

The application YAML also supports environment-backed application defaults (`AIVA_VOICE_TRANSACTION_AUTH_*` and per-operation `AIVA_VOICE_*_AUTH_REQUIRED`). Test bypass is not a supported property.

## Validation

Tests cover default-deny behavior, mode handling, per-operation authentication requirements, TTL bounds, override precedence/isolation, Phase 2A.1 challenge integration, Portal compatibility, session expiry/cleanup, and the invariant that no transaction executes.
