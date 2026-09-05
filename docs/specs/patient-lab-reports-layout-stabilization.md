# Jeevanam Care Patient Lab Reports Layout Stabilization

## Status

Approved for implementation.

## Scope

Fix the `/patient/lab` patient-portal layout so the laboratory reports workspace uses the available desktop content width without changing laboratory workflow behavior.

In scope:

- patient lab page width and grid behavior
- summary card spacing and desktop wrapping
- lab order and report card readability
- long order identifier wrapping behavior
- status badge readability
- empty-state presentation for reports
- responsive behavior across desktop, tablet, and mobile

Out of scope:

- laboratory order lifecycle
- sample collection workflow
- report publication logic
- backend APIs and authorization
- patient portal navigation structure
- unrelated patient portal redesigns

## Ownership

- Frontend area: `web-care`
- Page component: `web-care/src/pages/patient/PatientLabPage.tsx`
- Shared patient styles: `web-care/src/styles.css`
- Patient portal shell remains shared unless a page-specific override is required for this layout defect

## Required Behavior

- desktop lab content should occupy the available patient portal content region
- summary cards should render in a horizontal grid on desktop
- lab orders and reports should use readable card widths
- long lab order IDs must not break character-by-character
- status badges must remain legible and not collapse into thin vertical strips
- empty reports should render as a normal empty-state panel
- mobile and tablet layouts must remain responsive and avoid horizontal overflow

## Validation

- verify `/patient/lab` renders a full-width content grid inside the patient shell
- verify summary metrics present three readable cards on desktop
- verify long order identifiers are truncated or wrapped safely without character-by-character breaks
- verify reports empty state remains readable
- verify mobile layout stacks without horizontal overflow
