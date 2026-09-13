# AIVA P1D: Unified Turn Interpreter

## Scope

Consolidate one patient transcript into a typed `PatientPortalCareAiCanonicalTurn`
before CareAI workflow routing and confirmation decisions. This is an additive
migration; existing deterministic resolvers, workflow registries, typed skills,
and state persistence remain authoritative for business execution.

## Semantic Boundary

`PatientPortalCareAiTurnInterpreter` is the single entrypoint for turn semantics.
It emits dialog act, intent, candidate entity text, confirmation polarity,
correction/alternative/selection references, conversation-control flags, source,
and confidence.

The deterministic fast path handles unambiguous controls, confirmations, dates,
times, selections, and simple specialty turns. The existing LLM planner is an
internal Gemini interpretation signal for richer turns and now has additive
structured fields for dialog act, correction, alternative, and selection.

Neither interpreter nor planner may return or mutate doctor IDs, clinic IDs,
slot IDs, authorization, availability, booking status, or patient assertions.

## Runtime Authority

CanonicalTurn is the semantic input to workflow intent/control decisions. Entity
resolvers validate candidate text before state mutation. Workflow/state registries
and typed skills remain the only authorities for transitions, authorization,
confirmation enforcement, and writes.

## Compatibility and Migration

This first slice preserves the existing `applyIntent` and skill execution paths
while routing intent and confirmation decisions through CanonicalTurn. Subsequent
turn-interpreter migrations can move remaining raw-text entity promotion behind
the same boundary without creating a second planner or extractor authority.

## Verification

Focused tests cover natural booking turns, corrections, alternatives, selection,
end/abandon semantics, negative confirmation precedence, and the existing CareAI
service suite.
