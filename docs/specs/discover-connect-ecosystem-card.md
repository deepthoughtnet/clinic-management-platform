# Discover Connect Ecosystem Card

## Status

Approved for implementation.

## Scope

Add a new homepage ecosystem card named `Jeevanam Connect` to the Discover home page.

The homepage ecosystem strip must present the Jeevanam products in this order:

1. Jeevanam Discover
2. Jeevanam Connect
3. Jeevanam Care
4. Jeevanam Healthcare

## Product Boundary

- `Jeevanam Discover`: patient/public discovery.
- `Jeevanam Connect`: provider-facing participation and workspace entry.
- `Jeevanam Care`: patient care journey.
- `Jeevanam Healthcare`: operational clinic/hospital application.

## In Scope

- Homepage card copy and ordering.
- Responsive layout for the ecosystem strip.
- Existing provider entry route reuse for the Connect CTA.
- Frontend tests covering card presence, order, and navigation label.

## Out of Scope

- Backend domains, provider lifecycle, moderation, publication, or persistence.
- New provider workflow routes.
- Changes to For Providers navigation behavior.
- Any patient, doctor, clinic, or hospital profile logic.

## Validation

- Homepage renders the new Connect card in the correct order.
- Connect CTA points to the existing provider journey route.
- Discover, Care, and Healthcare cards remain unchanged aside from ordering.
- Responsive layout remains usable on desktop, tablet, and mobile.
