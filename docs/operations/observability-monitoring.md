# Platform Observability & Monitoring v1

## Overview
Platform Observability v1 adds tenant-scoped operational visibility across Clinic Platform, CarePilot, and AI surfaces through `/api/ops/*` endpoints and the Administration `Platform Ops` page (`/admin/platform-ops`).

## APIs
- `GET /api/ops/platform-health`: aggregated health snapshot (`HEALTHY/DEGRADED/WARNING/CRITICAL`) with scheduler, queue, provider, AI, webhook, and integration readiness signals.
- `GET /api/ops/schedulers`: scheduler status list.
- `GET /api/ops/queues`: queue/backlog metrics.
- `GET /api/ops/providers`: provider readiness + operational stats scaffold.
- `GET /api/ops/ai-metrics`: AI invocation usage/cost/latency summary.
- `GET /api/ops/webhooks`: webhook pipeline metrics scaffold.
- `GET /api/ops/alerts`: operational alerts list.
- `GET /api/ops/runtime/summary`
- `GET /api/ops/runtime/errors`
- `GET /api/ops/runtime/failures`

## Scheduler Monitoring
The dashboard reuses:
- `CarePilotRuntimeSchedulerMonitor` for reminder scheduler scan visibility.
- `CarePilotAiCallSchedulerMonitor` for AI call scheduler run health (`processed/dispatched/failed/skipped/duration`).

## Queue Monitoring
Queue metrics are tenant-scoped and currently include:
- `campaign-executions`
- `ai-call-executions`

Tracked dimensions:
- queue size
- pending
- retrying
- failed
- processing
- stale
- throttled
- suppressed

## Provider Monitoring
Provider health is composed from existing integrations status (`/api/admin/integrations/status`) and exposed in ops shape:
- status/readiness per provider
- enabled/configured flags
- provider name
- placeholders for success/failure/latency signals (safe foundation for incremental enrichment)

## AI Observability
AI metrics are sourced from AI orchestration usage summary:
- total/success/failed call counts
- token usage
- estimated cost
- average latency
- calls by provider/use case/status

## Alerts Foundation
New table:
- `platform_operational_alerts`

Schema:
- `id`
- `tenant_id` (nullable for future global/system alerts)
- `alert_type`
- `severity` (`WARNING`, `CRITICAL`)
- `source`
- `message`
- `status` (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`)
- `created_at`
- `resolved_at`

This v1 adds retrieval foundation (`GET /api/ops/alerts`). Automated alert generation/escalation can be layered without changing API shape.

## Correlation & Diagnostics
Operational APIs rely on existing request context and correlation plumbing in platform-spring (`RequestContextFilter`, correlation ID propagation). Runtime diagnostics endpoints aggregate recent failure indicators and retry/staleness signals.

## RBAC
Endpoints are protected for tenant context roles:
- `CLINIC_ADMIN`: full tenant ops visibility.
- `AUDITOR`: read-only visibility.
- `PLATFORM_ADMIN` + `PLATFORM_TENANT_SUPPORT`: tenant-selected cross-tenant support visibility.
- Other clinic roles are denied by backend policy.

## Future Roadmap
- Add deeper delivery/webhook/event latency metrics from immutable audit tables.
- Auto-generate and lifecycle-manage operational alerts from threshold rules.
- Add Prometheus/Grafana exporters and dashboards when external observability stack is introduced.
- Add drill-down links from Platform Ops cards to CarePilot Ops and AI Ops detailed views.
