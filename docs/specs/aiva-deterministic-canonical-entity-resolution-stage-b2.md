# AIVA Deterministic Canonical Entity Resolution Stage B2

## Scope

Extend the deterministic CareAI canonical-resolution pattern from specialty to the remaining high-value entities used by patient portal conversation state:

- doctor
- clinic
- service
- location
- selection / ordinal

## Goals

- Resolve doctor, clinic, service, location, and candidate-selection phrases deterministically.
- Keep resolution catalog-aware and conservative.
- Preserve resolved values in CareAI conversation state across turns.
- Reuse the existing single-orchestrator CareAI architecture.

## Constraints

- Do not add a parallel orchestrator or multi-agent design.
- Do not change booking execution, confirmation policy, discovery publication, provider visibility, or entitlement rules.
- Do not create channel-specific state.
- Do not invent unsupported catalog values.

## Deliverables

- Shared canonical resolution result model
- Doctor, clinic, service, location, and selection resolvers
- Entity registry and extractor integration
- CareAI state promotion for resolved values
- Regression tests for exact, alias, partial, ambiguous, unresolved, and candidate-selection flows

