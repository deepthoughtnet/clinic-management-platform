# CarePilot Backend Foundation (V1)

## Scope
This phase establishes a modular CarePilot backend foundation inside the existing monorepo, with additive APIs, tenant-scoped persistence, and scheduler-safe execution transitions.

## Module Boundaries
- `carepilot-domain` owns campaign/template/execution data and state transitions.
- API-BFF owns HTTP controllers and schedule trigger wiring.
- Provider integrations remain out of scope and must be implemented later under `backend/providers/*` through SPI adapters.

## Feature Flag and Entitlement
CarePilot API routes are module-gated through existing module entitlement interception.
Current legacy fallback maps `CAREPILOT` module checks to the existing tenant `teleCalling` entitlement until a dedicated tenant module flag is introduced.

## Outbox-Safe Direction
V1 updates execution rows only. Future phases should publish outbox events from execution transitions and let provider dispatch consume outbox events with retry/idempotency controls.
