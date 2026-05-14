# Platform Observability & Alerting V2

## Alert Lifecycle
- Statuses: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `SUPPRESSED`.
- Automated alert generation runs from platform ops reads and evaluates configured rules.
- Repeated incidents increment `occurrence_count` and update `last_seen_at`.
- Alerts can be acknowledged, resolved with notes, or suppressed from `/api/ops/alerts/{id}/*`.
- Auto-resolution closes alerts when rule conditions recover and `auto_resolve_enabled=true`.

## Rule Engine and Suppression
- Rule definitions are stored in `platform_alert_rules`.
- Supported thresholds: `COUNT`, `RATE`, `LATENCY`, `PERCENTAGE`, `STALENESS`.
- Cooldown (`cooldown_minutes`) suppresses duplicate storms and marks duplicates as `SUPPRESSED` inside cooldown windows.
- Escalation foundation stores level/state/target in `platform_alert_escalations` (no external paging yet).

## Provider SLOs
Endpoint: `GET /api/ops/provider-slos`
- Data sources: delivery attempts, webhook callbacks, retries, failures, AI invocation logs.
- Exposes: success rate, timeout rate, retry rate, average latency, failover usage placeholder, SLA breach flag, degradation flag.
- Coverage: Email/SMS/WhatsApp delivery rows + AI provider aggregate.

## Webhook Fidelity Monitoring
Endpoint: `GET /api/ops/webhook-metrics`
- Tracks incoming callbacks, processing failures, retry signals, stale callbacks, replay attempts, unknown provider payloads, DLQ-linked webhook failures, and avg processing latency.
- Signature/replay unknown counts are represented as platform-native metrics foundations and can be wired to richer provider-specific telemetry in later phases.

## Queue Anomaly Logic
- `QUEUE_BACKLOG_HIGH`: pending/retry backlog above threshold.
- `RETRY_STORM`: high count of executions with repeated attempts.
- `DLQ_SPIKE`: dead-letter growth over 30-minute window.
- `STALE_EXECUTIONS`: surfaced through queue stale metrics and runtime diagnostics.

## Scheduler Anomaly Logic
- `SCHEDULER_STALLED`: reminder scheduler heartbeat stale beyond threshold.
- `LOCK_CONTENTION`: high lock skip counts indicate contention.
- Scheduler diagnostics include lock acquisition/skip stats for reminder, campaign execution, AI dispatch, and AI reconciliation.

## AI Anomaly Logic
- `AI_PROVIDER_DEGRADED`: AI failure percentage threshold breach.
- `AI_COST_SPIKE`: AI estimated cost spike in 30-minute window.
- Failure/cost signals are derived from `ai_invocation_logs`.

## Operational Workflows
1. Review active/critical alerts in Platform Ops.
2. Acknowledge owned incidents to suppress duplicate triage.
3. Resolve after mitigation, with resolution notes.
4. Use provider SLO and webhook/queue/scheduler panels to isolate root cause.
5. Replay DLQ records for targeted recovery.
6. Use runtime summary for retry-storm and stale-execution pressure.

## Tenant Isolation and RBAC
- All alert/rule/action reads are tenant-scoped and rely on request tenant context.
- Role policy:
  - `PLATFORM_ADMIN`: full visibility and action.
  - `CLINIC_ADMIN`: tenant-scoped visibility and action.
  - `AUDITOR`: read-only visibility.
  - Other roles: denied.
