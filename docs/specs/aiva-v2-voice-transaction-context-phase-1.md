# AIVA V2 Voice Transaction Context — Phase 1

## Status

Approved implementation scope for explicit channel execution context and a default-deny transaction authorization contract. This phase does not enable Voice transactions.

## Owning boundary and placement

- AIVA V2 application orchestration and channel adapters remain in `backend/api/api-bff`.
- Appointment ownership, lifecycle validation, slot capacity, audit, events, and mutation remain owned by existing Patient Portal/application services and `appointment-domain`.
- No persistence/schema owner changes and no migration are expected.
- No frontend changes are in scope.

## Context contract

- Reuse `AivaExecutionContext` and distinguish caller identification (`identifiedPatientId`) from authenticated patient identity held only in the authorization grant.
- Carry tenant, channel, timezone, session/call, interaction, and correlation scope explicitly.
- Voice gets no transaction capabilities even when caller identification found one patient.
- Web derives its authorization grant from the existing authenticated Patient Portal request; `@PreAuthorize` and Patient Portal ownership checks remain unchanged.
- Do not create a `SecurityContext`, portal session, or `RequestContextHolder` for Voice.

## Authorization contract

- Capabilities are operation-specific: booking, viewing patient appointments, cancellation, and rescheduling.
- The shared gate denies when authentication, patient binding, tenant binding, session binding, expiry, or required capability is absent or mismatched.
- Read-only Voice discovery is unaffected. All four Voice transactional operations remain denied in this phase.
- No OTP/challenge or durable authorization-token storage is implemented.

## Compatibility and execution boundary

- The existing Web endpoint and Patient role authorization remain in place.
- Web orchestration constructs an explicit context from its authenticated request and passes it through the V2 decision and transaction authorization boundary.
- The existing `SessionProjection` and `AivaV2SessionStore` remain the only conversation/session mechanism; Web and Voice retain distinct tenant-scoped key strategies.
- The existing transactional kernel is not forked. Voice does not invoke it while its authorization grant is unauthenticated.
- `PatientPortalService` continues to resolve authenticated patient ownership and truthful app-user audit identity. These are not replaceable by caller phone identification.

## Validation

- Verify identified-only Voice context has no transaction grants.
- Verify the gate denies booking, patient appointment lookup, cancellation, and rescheduling before execution.
- Verify authenticated Web context is capability-authorized and existing portal behavior remains available.
- Verify tenant/session binding, Voice read-only behavior, active doctor continuity, session cleanup, serialization, and Spring wiring.
