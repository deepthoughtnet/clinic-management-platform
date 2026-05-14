# CarePilot AI Calls Foundation v1 + Operational Hardening v2

## Scope
CarePilot AI Calls provides tenant-safe outbound voice orchestration for campaigns and operational outreach.

v1 introduced campaign/execution/transcript foundation and provider SPI.
v2 hardens operations with scheduler dispatch, queue controls, retry/failover policy, webhook updates, event history, and reconciliation.

## Architecture
- `carepilot_ai_call_campaigns`: campaign-level policy and lifecycle.
- `carepilot_ai_call_executions`: per-call queue state, retries, suppression, failover metadata.
- `carepilot_ai_call_transcripts`: transcript and enrichment foundation fields.
- `carepilot_ai_call_events`: append-only event/timeline history.
- `voice-spi`: provider-neutral contracts.
- `MockVoiceCallProvider`: safe local/UAT execution.

## Queue Lifecycle
Statuses:
- `PENDING`, `QUEUED`, `DIALING`, `IN_PROGRESS`, `COMPLETED`
- `FAILED`, `NO_ANSWER`, `BUSY`, `CANCELLED`, `ESCALATED`
- `SKIPPED`, `SUPPRESSED`

Operational APIs support retry, cancel, suppress, reschedule, dispatch, event inspection, and transcript inspection.

## Scheduler Behavior
Config:
- `carepilot.ai-calls.scheduler.enabled`
- `carepilot.ai-calls.scheduler.fixed-delay`
- `carepilot.ai-calls.scheduler.batch-size`

Behavior:
- scans due executions in bounded batches
- respects campaign/tenant status and queue states
- applies tenant quiet-hours deferral before dispatch
- records per-run scheduler health summary

Health endpoint:
- `GET /api/carepilot/ai-calls/scheduler-health`

## Retry Policy
Config:
- `carepilot.ai-calls.retry.max-attempts`
- `carepilot.ai-calls.retry.initial-backoff-seconds`
- `carepilot.ai-calls.retry.max-backoff-seconds`

Retryable outcomes include `NO_ANSWER`, `BUSY`, temporary provider failures.
When max retries are reached, execution escalates with explicit reason.

## Provider Failover Strategy
Config:
- `carepilot.ai-calls.provider.primary`
- `carepilot.ai-calls.provider.fallback`
- `carepilot.ai-calls.provider.failover-enabled`

If primary returns retryable provider failure and failover is enabled, fallback provider is attempted and evented.
No provider failure is silently hidden.

## Webhook Contract Foundation
Endpoint:
- `POST /api/carepilot/ai-calls/webhooks/{provider}`

Behavior:
- maps provider callback status to internal execution status
- updates execution by `providerCallId`
- records event history
- upserts transcript enrichment fields when present
- handles unknown provider call IDs safely without crashing

## Throttling and Quiet Hours
Dispatch enforces tenant-safe limits:
- max calls per tenant per minute
- max concurrent calls per tenant
- max calls per patient per day

Calls breaching limits are `SKIPPED`/`SUPPRESSED` with reason.
Tenant quiet-hours settings are applied via notification-settings service before dispatch.

## Reconciliation
Config:
- `carepilot.ai-calls.reconciliation.enabled`
- `carepilot.ai-calls.reconciliation.stale-after`

Stale `DIALING` / `IN_PROGRESS` executions are reconciled into failure/retry path with event records.

## Transcript Enrichment Foundation
Supported persisted fields:
- transcript text
- summary
- sentiment
- outcome
- intent
- follow-up-required flag
- escalation reason
- extracted entities JSON

Realtime AI summarization/STT/TTS orchestration remains future scope.

## Future Roadmap
- vendor-specific webhook signature verification profiles
- provider polling adapters
- richer patient-level DND/consent integration
- realtime conversational voice agent orchestration
- STT/TTS and conversational state engine
