# AIVA Deterministic Specialty Resolution Foundation

## Scope

Harden CareAI/AIVA specialty resolution so that natural booking and doctor-discovery utterances normalize through one reusable deterministic resolver.

## Goals

- Resolve bare specialty phrases such as `General Medicine`, `General Physician`, `physician`, and `GP`.
- Keep extraction and normalization separate.
- Reuse one canonical resolver for booking and doctor discovery.
- Preserve the current single-orchestrator CareAI architecture.

## Constraints

- Do not add a new agent or parallel orchestration path.
- Do not change confirmation policy, availability, appointment execution, provider publication, or entitlement rules.
- Do not introduce channel-specific booking state.

## Deliverables

- `SpecialtyResolver`
- CareAI service integration for booking and doctor discovery
- Entity extractor integration for bare specialty candidates
- Entity registry metadata updates
- Regression tests for alias resolution and booking progression

