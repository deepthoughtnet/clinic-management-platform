# AIVA V2 Voice Transaction Scope Policy — Current Release

## Decision

The current AIVA Voice release supports doctor and specialty enquiries, availability enquiries, and creation of a new appointment. Voice OTP is outside this release because OTP generation and delivery are not available. Care login and historical Care verification are not prerequisites for new booking.

Rescheduling, cancellation, and retrieval of existing appointment or health-record data are unsupported by the Voice transaction scope. Requests for sensitive information must be rejected before invoking a clinical or patient-owned data service. The user should be directed to Care.

## Action matrix

| Action | Current Voice behavior |
|---|---|
| Doctor enquiry | Allow under existing enquiry policy |
| Specialty enquiry | Allow under existing enquiry policy |
| Availability enquiry | Allow under existing availability policy |
| Book a new appointment | Allow for a unique active tenant-scoped patient match; no OTP. Guest booking only when the existing guest policy and registration flow allow it |
| Reschedule an existing appointment | Not supported; no lookup, OTP, or mutation |
| Cancel an existing appointment | Not supported; no lookup, OTP, or mutation |
| View reports, prescriptions, lab results, history, consultations, timeline, or other sensitive data | Not supported; refer to Care before data lookup |

## Booking authorization

A uniquely matched active patient receives only the existing `VOICE_REGISTERED_UNVERIFIED` booking actor, bound to the trusted tenant, patient, call, and `BOOK_APPOINTMENT` operation. This context does not grant Care access or patient-data capabilities. The normal provider canonicalization, availability, slot validation, confirmation binding, authorization boundary, idempotency, and booking application service remain unchanged.

Inbound caller resolution and identity assurance are distinct. A unique tenant-scoped caller-phone match is retained as `conversationParty=IDENTIFIED_PATIENT` and as the call session's booking subject, while its assurance remains `REGISTERED_UNVERIFIED`; `phoneBindingVerified` remains false. The adapter must preserve this patient binding across turns and reject a conflicting patient context for the same call. This retained identity is used only for restricted new-booking binding and does not authorize patient-data access.

Deterministic courtesy-close phrases (including “Okay. Thank you.”, “No, thank you.”, Hindi, and Hinglish equivalents) take precedence over booking context and clarification recovery. They close the Voice conversation without invoking a business tool or booking execution boundary.

When no unique patient match exists, booking remains unavailable unless guest booking is explicitly enabled and the existing consent, minimum-registration, tenant-scoped duplicate lookup, and call-bound guest capability flow completes. Guest registration does not provide Care or health-record access. If guest booking is disabled, Voice must not create a patient and must not fall back to OTP.

## OTP compatibility

`AIVA_VOICE_OTP_REQUIRED` may remain as a future-compatibility configuration property, but it must not gate `BOOK_APPOINTMENT` in this release. Voice must not start or verify OTP challenges. Reschedule and cancellation are disabled, not OTP-gated. No SMS provider, development OTP, or test bypass is part of this release policy.

Care authentication and OTP behavior are unchanged. This policy does not consume Care verification evidence or alter its persistence.

## Response behavior

Unsupported reschedule/cancellation requests should receive concise localized guidance equivalent to: “Rescheduling and cancellation are not available through AIVA Voice right now. Please use Care.” Sensitive-data requests should receive guidance equivalent to: “Please use Care to view your health information.” Do not reveal whether an appointment or record exists.

## Boundaries and compatibility

- `VoiceReadOnlyAivaService` is the Voice scope boundary and must reject unsupported protected actions before business data tools run.
- `BookingExecutionContextFactory` continues to issue operation-limited contexts: existing-patient restricted booking and the existing guest capability both allow only `BOOK_APPOINTMENT`.
- The generic `VoiceTransactionPolicyResolver` OTP field is retained for compatibility but is not an AIVA Voice booking prerequisite in this release.
- No appointment lifecycle, booking mutation, provider canonicalization, availability, stale-slot, tenant isolation, or confirmation semantics are changed.
- Web and alternate Voice connector behavior are outside this policy change.

## Verification

Focused coverage should prove: matched existing patient booking without Care session/Voice OTP; guest booking under the existing guest gate; no patient creation when guest booking is disabled; no OTP challenge; reschedule/cancellation blocked before lookup or mutation; sensitive-data requests blocked before protected service calls; and preservation of tenant, patient, confirmation, and booking execution boundaries.

## Local runtime certification harness

The transcript harness at `/api/local/voice-certification` exists only for local application-runtime certification. It requires the `docker` and `voice-certification` Spring profiles, the explicit `aiva.voice.certification-harness.enabled=true` property, and the existing `PLATFORM_ADMIN` permission. It is disabled by default and has no production configuration entry.

Starting a simulated inbound call requires caller number and DID only. Tenant, clinic, and patient are derived through the existing inbound DID/channel resolver and tenant-scoped caller match. A client cannot supply a tenant, patient ID, booking capability, or execution context. Subsequent transcript turns use the call ID returned by the start operation and enter `VoiceReadOnlyAivaService.continueTurn`, preserving normal transaction policy, adapter, session, tools, booking authorization, and response rendering. The harness skips audio transport/STT and does not certify physical telephony or acoustic recognition. It performs no OTP/SMS behavior.

## Future scope

Reschedule and cancellation require a separately approved verification capability and action-specific policy. Care/clinical record retrieval remains outside Voice until its identity assurance, authorization, privacy, and data-access contracts are explicitly approved.
