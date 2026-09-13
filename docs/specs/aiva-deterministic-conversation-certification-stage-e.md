# AIVA Deterministic Conversation Certification Corpus

## Status

Approved implementation spec for Stage E.

## Scope

This stage certifies the existing AIVA/CareAI orchestration behavior through an executable conversation corpus.

It does not introduce a new architecture.

It validates:

- canonical entity resolution
- specialty, doctor, clinic, service, location, and selection normalization
- typed deterministic skills
- confirmation and authorization gates
- explicit workflow sub-states
- state persistence across turns
- correction and backtracking
- shared voice/text orchestration behavior

## In scope

- Test corpus generation for utterance certification
- Test corpus generation for multi-turn conversation certification
- Deterministic supported-path scenarios
- Clarification and unresolved-path scenarios
- Corpus reporting of pass/fail counts by language and workflow family

## Out of scope

- New domain features
- Provider, Discover, or entitlement rule changes
- Voice transport changes
- Workflow redesign
- Multi-agent orchestration

## Validation

The corpus must run as executable tests in `api-bff` and preserve the existing green build.
