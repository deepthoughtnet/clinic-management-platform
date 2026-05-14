# Backup and Recovery Runbook

## Purpose
Operational foundation for production backup/restore and post-restart queue recovery for Clinic + CarePilot + AI services.

## Database Backup Strategy
- Use PostgreSQL physical backups for full-cluster recovery.
- Use logical backups (`pg_dump`) for table-level portability and audit sampling.
- Minimum cadence:
  - nightly full backup
  - frequent WAL/archive shipping for point-in-time recovery

## Restore Verification
1. Restore into isolated staging environment.
2. Run Flyway migration validation.
3. Verify critical table counts:
   - patient/appointment/billing core tables
   - carepilot campaign execution tables
   - ai invocation and ai call execution tables
   - `platform_dead_letter_events`
4. Execute smoke checks:
   - login and tenant selection
   - scheduler health endpoints
   - Platform Ops page load

## Scheduler/Queue Recovery After Restart
- Schedulers are lock-protected and safe for multi-instance restarts.
- Due campaign executions and AI calls remain persisted and are picked up on next run.
- Reconciliation scheduler recovers stale AI-call executions via existing stale handling.

## Webhook Recovery
- Review failed callback signals in operational dashboards.
- For replayable failures, use module-specific retry/replay controls.
- Use DLQ replay endpoint for dead-lettered campaign/AI-call execution flows.

## DLQ Recovery
1. Open Platform Ops → Dead Letter section.
2. Inspect source/failure/retry context.
3. Trigger replay per record.
4. Confirm status transition (`PENDING` → `REPLAYED` or `REPLAY_FAILED`).

## Operational Notes
- Keep backup encryption and access controls aligned with policy.
- Never include secrets in operational exports.
- Periodically run restore drills and document RTO/RPO attainment.
