# AI Orchestration Platform

## Scope
Covers `backend/domains/ai-domain` + AI APIs in `api-bff`, and AI Ops admin interface.

## Core Components
- Orchestration service (`AiOrchestrationServiceImpl`)
- Provider router (`AiProviderRouter`) with provider candidates
- Providers: Gemini adapter, Groq adapter, Mock provider
- Prompt template registry for legacy/copilot templates
- Platform hardening module (`ai/orchestration/platform/*`) for prompt registry v1

## Prompt Registry and Versioning
- Tables:
  - `ai_prompt_definitions`
  - `ai_prompt_versions`
- APIs:
  - list/get/create definition
  - create version
  - activate/archive version
- One active version at a time per definition enforced in service logic.

## Invocation Logs and Usage
- `ai_invocation_logs` captures request IDs, provider/model, status, tokens, cost estimate, latency, and redacted summaries.
- Usage summary endpoint aggregates totals by provider, use case, and status.

## Guardrails Foundation
- `ai_guardrail_profiles` table.
- Orchestration pre-execution validation hook for token and prompt safety constraints.

## Tool and Workflow Foundations
- `ai_tool_definitions`: future-safe registry with risk/approval flags and schemas.
- `ai_workflow_runs` + `ai_workflow_steps`: workflow telemetry foundation.

## API Surface
- AI Copilot: `/api/ai/status`, clinical AI generation endpoints.
- AI Ops: `/api/ai/prompts*`, `/api/ai/invocations`, `/api/ai/usage/summary`, `/api/ai/tools`, `/api/ai/guardrails`, `/api/ai/workflows/*`.

## Integration with CarePilot AI Calls
- AI Calls transcript foundation can feed future enrichment and AI ops analytics.
- Current integration focuses on orchestration readiness and observability, not realtime conversation intelligence.

## Provider Lifecycle
- Provider selection by task type.
- Failures normalized to safe fallback responses where applicable.
- Provider/model names recorded in audits and invocation logs.

## Future Agent Runtime Direction
- Tool execution mediation from tool registry.
- Multi-step workflow runtime using workflow run/step telemetry.
- Advanced guardrails and approval checkpoints.

