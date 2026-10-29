# AIVA V2 Voice Registered Patient Authentication — Phase 2A

## Scope

Add consent-gated registered-patient OTP verification to the existing call-scoped AIVA V2 Voice session. This phase issues a short-lived authorization grant only; transactional execution remains disabled. Guest registration, booking, lookup, cancellation, and rescheduling execution are excluded.

## Placement and dependencies

- AIVA V2 routing/session orchestration remains in `backend/api/api-bff`.
- Reuse `PatientPortalOtpService` and `patient_portal_otp_challenges`; do not create a second OTP store or generator.
- Deliver through the existing provider-neutral messaging SPI; do not couple patient auth to Discover verification.
- Persist only a challenge reference and non-secret state in the existing `SessionProjection`.
- Keep Patient Portal endpoints and token issuance behavior unchanged.
- No frontend, appointment-domain, or migration changes are planned.

## Security contract

- Caller-phone identification is not authentication.
- Voice starts a challenge only after explicit caller consent, trusted tenant routing, and a unique identified patient whose registered mobile matches.
- Voice verification is bound to exact challenge ID, tenant, patient, call/session, and interaction; successful verification does not issue a Patient Portal token.
- Raw OTP is never persisted, logged, or included in Voice diagnostics/transcripts.
- The grant is short-lived and call-scoped. A separate execution-enabled policy remains false in this phase, regardless of granted capabilities.
- Session close/reset clears challenge and grant state.

## Compatibility and validation

Existing Web request/verify endpoints continue to use their existing response and token issuance paths. Tests cover challenge delivery, exact challenge/session binding, patient/tenant binding, retry/expiry, consent, transcript redaction, default-deny transaction execution, Web login regression, Redis serialization, session cleanup, and Spring wiring.
