# Administration AI Ops Scope and Telemetry

## Scope
- Route: `/admin/ai-ops`
- Audience: tenant-scoped administration users with AI Ops access
- Sources:
  - AI usage summary
  - invocation logs
  - prompt registry
  - tool registry
  - guardrails
  - workflow runs

## Confirmed behavior
- Usage summary represents the last 30 days by default.
- Invocation logs render the latest 20 tenant-scoped records from the recent feed.
- Summary and recent logs are not directly comparable without scope context.
- Output token and estimated cost metrics may be unavailable for some paths.
- When telemetry is unavailable, the UI must not present zero as a meaningful value.

## Validation rules
- Tenant scoping remains authoritative on the backend.
- Prompt registry actions continue to follow existing RBAC.
- No secret material is exposed in the AI Ops UI.

