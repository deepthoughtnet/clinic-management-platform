# CarePilot Domain Foundation

## Purpose
`carepilot-domain` provides the foundational backend building blocks for campaign automation and patient engagement workflows without introducing provider-specific integrations.

## Boundaries
- Owns campaign, template, and execution state.
- Exposes scheduling-safe execution state transitions.
- Provides feature-flag checks for tenant-level CarePilot activation.
- Does not send real messages and does not call external providers.

## Dependency Direction
Clinic workflows should emit facts/events that CarePilot can consume. CarePilot must remain additive and should avoid deep coupling to appointment, billing, or consultation internals.

## Extraction Strategy
The package layout keeps campaign/template/execution concerns isolated so the module can be extracted into a dedicated service with minimal contract churn.

## Provider Strategy
Provider-facing interfaces are represented through messaging SPI placeholders. Concrete provider adapters should be added under `backend/providers/*` in future phases.

## Outbox/Event Direction
Current v1 stores execution rows and status transitions. Future phases can publish outbox events from execution transitions for reliable asynchronous provider dispatch.
