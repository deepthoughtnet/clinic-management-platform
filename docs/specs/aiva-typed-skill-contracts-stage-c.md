# AIVA Typed Skill Contracts Stage C

## Scope

Promote the existing CareAI ToolRegistry from metadata-only descriptors into strongly typed deterministic skill contracts while preserving the single-orchestrator architecture.

## Goals

- Define a minimal generic `AivaSkill<I, O>` contract for deterministic CareAI execution.
- Promote the existing patient portal tool registry into typed skill definitions and executors.
- Preserve the orchestrator as the owner of workflow state, confirmation, and response composition.
- Reuse the existing deterministic business lookup and booking execution paths.
- Ensure `doctor.find` merges authorized Care/Health doctors with published Discover doctors so private platform-registered doctors remain resolvable for authenticated patients.
- Keep voice and text channels on the same orchestration core.

## Constraints

- Do not introduce a parallel orchestrator or multi-agent architecture.
- Do not change Provider, Discover publication, entitlement, voice transport, or confirmation policy behavior.
- Do not introduce database migrations or persistence changes.
- Do not create separate tool and skill registries.
- Do not let skills own global conversation state.

## Deliverables

- Generic typed skill contract and structured outcome model
- Typed skill inputs and outputs for doctor, clinic, service, availability, appointment check/book/cancel/reschedule
- Evolved ToolRegistry that binds metadata to typed skill executors
- Orchestrator integration that invokes skills instead of bypassing the tool boundary
- Regression tests for skill metadata, execution, and orchestration flow

## Staged Implementation Plan

1. Introduce the typed skill contract and outcome types.
2. Evolve the existing registry to hold typed skill executors and metadata together.
3. Add the first skill set:
   - doctor.find
   - clinic.find
   - service.find
   - availability.check
   - appointment.check
   - appointment.book
   - appointment.cancel
   - appointment.reschedule
4. Route the existing CareAI service helpers through the skill boundary.
5. Add focused tests for the registry, skills, and orchestration regression.
