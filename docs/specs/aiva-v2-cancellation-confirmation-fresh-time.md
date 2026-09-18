# AIVA V2 Cancellation Confirmation and Fresh-Time Selection

## Status

Approved implementation scope for multilingual cancellation confirmation and fresh exact-time selection, with safe stale-branch diagnostics and real-session confirmation regressions.

## Goal

Normalize clear Hinglish affirmative forms into the existing typed positive-confirmation fact. For fresh cancellation requests, preserve only user-supplied typed ordinal or exact time as an authoritative selection, apply it to authorized lookup candidates, and do not let semantic-provider candidate selection choose an appointment on the user's behalf.

## Boundaries

Changes are limited to the `api-bff` AIVA language adapter, decision gateway, transactional kernel candidate selection, and focused tests. Existing bounded candidate selection, cancellation capability creation/invocation, domain services, authorization, tenant isolation, provider routing, booking, reschedule, persistence, and voice transport remain unchanged.

## Acceptance

- `haan`, `theek hai`, and `thik hai`, optionally followed by `kar do`, produce positive confirmation; `kar do` alone does not.
- A pending cancellation confirmation maps typed positive confirmation to deterministic `CONFIRM_CANCELLATION` without invoking the semantic provider.
- Fresh cancellation exact-time selection is applied only to authorized lookup candidates.
- Provider-generated ordinal/reference cannot override typed user selection or select the first candidate absent a user selection.
- Mixed-script doctor extraction is changed only if an adapter test demonstrates it is missing.
- Existing active bounded ordinal/time selection and negative cancellation behavior remain unchanged.
- Missing, expired, and domain-rejected confirmation capabilities retain distinct safe diagnostic reason codes without changing outward stale behavior or expiry.
- Direct-time and ambiguity-to-ordinal cancellation confirmation succeed across real service turns with the same tenant/patient/conversation session key.
- Logs contain only approved safe confirmation metadata; domain exception messages and PHI are not logged.

## Validation

Adapter, gateway, cancellation kernel, multilingual cancellation vertical, full AIVA tests, API BFF package, and `git diff --check`.
# Cancellation domain rejection diagnostics

This diagnostic-only addition distinguishes cancellation rejection at the
Patient Portal boundary without changing cancellation behavior, tenant
selection, authorization, lifecycle rules, or idempotency handling.

The service emits safe cancellation metadata for current-tenant visibility,
visibility within already-authorized Care tenants, owner/current tenant match,
appointment status category, whether `AppointmentService.updateStatus` was
reached, and a bounded rejection reason. Diagnostic reads use only current and
already-authorized patient scopes. Failures during diagnostic reads do not
replace the original cancellation result.

Focused service tests cover current-tenant visibility, visibility in an
authorized other tenant while preserving the existing rejection, an appointment
outside authorized scopes, and an `IN_CONSULTATION` transition rejection. No
transaction or authorization behavior is changed.

## Authorized owner-tenant transaction

The cancellation capability now retains the authoritative owning tenant from
the authorized appointment summary. Confirmation may use the additive
`PatientPortalService.cancelAppointmentInOwningTenant` application path, which
revalidates the owner tenant, patient-scoped appointment, and Care authorization
before invoking the existing appointment status transition. Idempotency is
scoped to the resolved owner tenant. The request's current Care clinic is not
changed, and the appointment domain lifecycle contract remains authoritative.
