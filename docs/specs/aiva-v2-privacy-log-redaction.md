# AIVA V2 Privacy, Log Redaction, and Semantic-Provider Minimization

## Scope

This specification covers typed AIVA V2 orchestration diagnostics and the
semantic-provider boundary. It does not change provider ordering, workflow
semantics, authorization, domain transactions, or voice behavior.

## Policy

- AIVA logs contain operation/category/status metadata and presence booleans,
  not raw turns, normalized text, healthcare dates/times, names, appointment
  identifiers, capability references, or serialized session state.
- Conversation correlation values in AIVA logs are irreversible short SHA-256
  prefixes. Raw tenant, patient, and conversation identifiers remain internal.
- AIVA semantic requests may contain the current user-visible linguistic facts
  needed for interpretation, such as the requested doctor/date/time language,
  but never authoritative IDs, capability contents, idempotency keys, or raw
  conversation history.
- Provider response logs contain metadata such as length bucket, parse result,
  finish reason, and failure category; provider response bodies are not logged.
- Redis session state is operational workflow state with the existing fixed TTL.
  It does not contain raw transcript history, prompts, provider responses, auth
  tokens, or API keys. Replay response data is retained only as required for
  exact duplicate replay.

## Operational metrics

The approved low-cardinality metric vocabulary is `aiva_turn_total`,
`aiva_turn_latency`, `aiva_provider_attempt_total`,
`aiva_provider_failure_total`, `aiva_tool_invocation_total`,
`aiva_turn_disposition_total`, `aiva_session_expiry_total`,
`aiva_duplicate_replay_total`, and `aiva_sequence_rejection_total`.
Labels are limited to workflow/operation, provider, outcome category, and
bounded status classes. Patient, doctor, appointment, tenant, conversation,
capability, and free-text values are never labels.

## Validation

Provider payload tests and AIVA trace tests must assert that synthetic patient,
appointment, slot, confirmation, and idempotency markers do not cross the
semantic-provider boundary or appear in redacted diagnostics.
