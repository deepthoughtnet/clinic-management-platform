# AI Orchestration Platform Architecture

This architecture supplements the detailed product view in [`../ai-platform/ai-orchestration-platform.md`](../ai-platform/ai-orchestration-platform.md).

## Architectural Layers
- API layer: AI endpoints + AI Ops endpoints.
- Orchestration layer: request normalization, provider routing, guardrail checks.
- Provider layer: Gemini/Groq/Mock adapters implementing common AI provider contracts.
- Governance layer: prompt registry/versioning, guardrail profiles, invocation logs.
- Observability layer: usage summary aggregation and workflow/tool registry foundations.

## Architecture Diagram
```mermaid
flowchart TB
  Client --> AiAPI[/api/ai/*]
  AiAPI --> Orch[AiOrchestrationServiceImpl]
  Orch --> Guard[AiGuardrailService]
  Orch --> Router[AiProviderRouter]
  Router --> P1[Gemini Adapter]
  Router --> P2[Groq Adapter]
  Router --> P3[Mock Adapter]
  Orch --> Audit[AiRequestAuditService]
  Orch --> Inv[AiInvocationLogService]
  Inv --> Logs[(ai_invocation_logs)]
  OpsAPI[/api/ai/prompts|usage|tools|guardrails|workflows] --> Registry[Platform Services]
  Registry --> DB[(V042 AI Tables)]
```

