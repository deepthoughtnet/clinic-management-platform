# Enterprise Production Hardening v1

## Scope Delivered
- Distributed scheduler locking using PostgreSQL advisory locks.
- Scheduler concurrency safety and lock-skip diagnostics.
- Dead-letter queue (DLQ) foundation with operational replay APIs.
- Platform Ops enhancements for lock visibility and DLQ status.

## Distributed Locking Strategy
- Implemented in `platform-spring` as `PostgresAdvisoryLockService`.
- Lock keys are deterministic scheduler identifiers.
- Uses `pg_try_advisory_lock` with bounded wait and explicit unlock.
- If lock is unavailable, scheduler run is skipped safely.

## Scheduler Hardening
Schedulers hardened with distributed lock protection:
- `CarePilotReminderScheduler`
- `CarePilotSchedulerService` (campaign execution scheduler)
- `CarePilotAiCallScheduler`
- `CarePilotAiCallReconciliationScheduler`

Behavior:
- Single-run across multi-instance deployments.
- Skip-on-lock-contention instead of duplicate dispatch.
- Lock acquisition/skip timestamps and counters captured via `SchedulerLockMonitor`.

## DLQ Foundation
New table:
- `platform_dead_letter_events`

Recorded fields:
- source type (`CAMPAIGN_EXECUTION`, `AI_CALL_EXECUTION`)
- source execution id
- failure reason
- payload summary
- retry count
- recovery state (`PENDING`, `REPLAYED`, `REPLAY_FAILED`)

APIs:
- `GET /api/ops/dead-letter`
- `POST /api/ops/dead-letter/{id}/replay`

Replay behavior:
- Campaign dead letters replay via existing `CampaignExecutionService.retryExecution`.
- AI call dead letters replay via existing `AiCallOrchestrationService.retry`.

## Platform Ops Enhancements
- Scheduler section includes lock metrics:
  - last lock acquired at
  - last lock skipped at
  - acquire count
  - skip count
- Added DLQ section with replay actions.

## Security and Safety
- Tenant-scoped operations for list/replay.
- RBAC enforced on operational endpoints.
- No provider secrets exposed through hardening APIs.

## Remaining Hardening Roadmap
- Retry storm suppression thresholds and provider cooldown governance.
- Circuit-breaker primitives around provider adapters.
- Cache strategy + TTL governance for non-sensitive operational/config data.
- Datasource pool tuning visibility in Platform Ops.
- Automated alert rules for retry storms, stale jobs, and lock contention spikes.
