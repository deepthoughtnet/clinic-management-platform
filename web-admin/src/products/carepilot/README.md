# CarePilot Product Placeholders

This folder prepares a low-risk product boundary for future CarePilot modules.

## Purpose

- Keep CarePilot UI scaffolding separate from clinic operations pages.
- Avoid risky route rewrites while product capabilities are still evolving.
- Provide predictable locations for future implementation.

## Current State

- Placeholder pages only.
- No backend/API wiring.
- No operational workflows.

## Planned Modules

- campaigns
- messaging
- reminders
- engagement
- leads
- webinars
- ai-calls

## Boundary Guidance

- Existing clinic pages stay in `src/pages` until feature migration is approved.
- Shared CarePilot-only presentational components live in `shared`.
- Cross-product shared components should remain in global `src/components`.
