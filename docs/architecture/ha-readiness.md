# High Availability Readiness (v1)

## Current HA Baseline
- Stateless API tier with externalized tenant context.
- Persistent execution queues for CarePilot and AI Calls.
- Flyway-managed schema consistency.
- Existing runtime/ops observability endpoints.

## HA Hardening Added
- Cluster-safe scheduler coordination via PostgreSQL advisory locks.
- Duplicate multi-instance scheduler execution suppression.
- Lock acquisition/skip operational visibility in Platform Ops.
- Dead-letter persistence and replay foundation for failed execution recovery.

## Scheduler Safety Model
- Each critical scheduler uses deterministic lock keys.
- When a node cannot acquire lock:
  - it skips safely
  - records lock-skip telemetry
- Prevents duplicate dispatch storms during rolling deployments or horizontal scale-out.

## Rolling Deployments
- During rolling restarts, lock ownership naturally shifts across healthy nodes.
- Persisted queues guarantee work continuity after node replacement.
- Reconciliation scheduler helps recover stale in-progress AI-call executions.

## Remaining HA Roadmap
- Lock-owner identity + heartbeat persistence for cross-node diagnostics.
- Provider circuit-breaker state sharing across nodes.
- Multi-region data/traffic strategy.
- Automated failover runbooks with health-gated traffic shifting.
