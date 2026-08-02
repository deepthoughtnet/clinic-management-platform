# Jeevanam Care Batch 2A: Shared Shell and Dashboard Cleanup

## Status

Approved for patient portal shell and dashboard presentation cleanup.

## Scope

Refine the authenticated Jeevanam Care patient shell and dashboard without changing patient business flows.

In scope:

- shared authenticated Care layout cleanup
- dashboard summary and section presentation
- duplicate patient identity reduction
- patient-friendly labels and empty states
- independent loading and retry states for dashboard widgets
- responsive behavior for dashboard and shell surfaces

Out of scope:

- authentication contracts
- OTP flows
- provider workflows
- clinical logic
- billing calculations
- appointment business rules
- Book Visit redesign
- appointment, prescription, bills, notifications, lab reports, AIVA, and profile redesigns

## Frontend Ownership

- Frontend area: `web-care`
- Shared authenticated shell: `web-care/src/components/CareShell.tsx`
- Patient portal pages: `web-care/src/pages/patient/PatientPortalPages.tsx`
- Shared patient styles: `web-care/src/styles.css`

## Validation

- duplicate dashboard identity card is removed
- patient phone is not repeated unnecessarily
- dashboard summary cards use patient-friendly labels
- appointment, prescription, bill, lab report, notification, and AIVA widgets load independently
- empty, loading, and error states are present with retry where appropriate
- mobile layout remains usable without horizontal overflow

