# Jeevanam AIVA Stage D: Formal Workflow Sub-States

## Status
Approved implementation note for Stage D.

## Scope

Make CareAI workflow progression explicit and testable by introducing a first-class workflow sub-state model for the patient portal orchestrator.

## Constraints

- Keep one orchestrator.
- Keep existing typed skills and deterministic resolvers.
- Do not change Provider, Discover, entitlement, or voice transport behavior.
- Do not add multi-agent orchestration.
- Do not change persistence schema or add migrations.

## Intent

The service already models top-level workflows such as:

- `BOOK_APPOINTMENT`
- `RESCHEDULE_APPOINTMENT`
- `CANCEL_APPOINTMENT`
- `CHECK_APPOINTMENT`
- `FIND_DOCTOR`
- `FIND_CLINIC`

Stage D adds explicit sub-states such as:

- `NEED_PROVIDER_OR_SPECIALTY`
- `FINDING_PROVIDERS`
- `NEED_PROVIDER_SELECTION`
- `NEED_DATE`
- `CHECKING_AVAILABILITY`
- `NEED_SLOT_SELECTION`
- `CONFIRMATION_PENDING`
- `EXECUTING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

These sub-states must be:

- persisted in existing workflow context JSON
- visible in service responses for diagnostics and tests
- driven by structured skill outcomes and deterministic transitions
- preserved across voice/text turns

## Transition policy

- Top-level workflow remains separate from sub-state.
- Illegal sub-state transitions must be rejected by a registry/policy helper.
- Corrections such as changing doctor/date/time must move the flow back to a legal earlier sub-state.
- Confirmation must remain mandatory for write actions.

## Validation

Stage D requires focused registry and service regression tests plus a backend package build success.
