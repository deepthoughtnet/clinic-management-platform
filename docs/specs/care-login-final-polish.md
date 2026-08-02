# Jeevanam Care Login Final Polish

## Status

Approved for Batch 1 presentation polish.

## Scope

Refine the Jeevanam Care patient login page only.

In scope:

- keep the main welcome heading on one line on standard desktop widths
- replace the temporary placeholder-style illustration with a compact care-focused illustration
- compact the left panel hierarchy
- keep OTP/session behavior unchanged
- preserve the authenticated Care layout

Out of scope:

- patient dashboard redesign
- authentication contracts
- OTP APIs
- routing
- backend changes
- database changes
- booking workflow
- authenticated header/footer redesign

## Frontend Ownership

- Frontend area: `web-care`
- Shared shell components: `web-care/src/components/CareShell.tsx`
- Patient login page: `web-care/src/pages/patient/PatientPortalPages.tsx`
- Presentation styles: `web-care/src/styles.css`

## Validation

- desktop heading remains on one line at the normal viewport
- login form remains the primary action area
- illustration stays decorative, compact, and responsive
- no authentication or session behavior regresses
- source-based regression tests cover the login page copy and illustration markup

