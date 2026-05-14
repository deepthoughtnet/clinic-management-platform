# Operations Runbook

## 1. Daily Health Checks
- API health/metrics endpoints (actuator).
- CarePilot failed executions list.
- AI Calls scheduler health endpoint.
- Messaging provider status and webhook intake health.

## 2. Scheduler Monitoring
Monitor scheduled components:
- `NotificationReminderScheduler`
- `ClinicalAiJobProcessor`
- `CarePilotReminderScheduler`
- `CarePilotSchedulerService`
- `CarePilotAiCallScheduler`
- `CarePilotAiCallReconciliationScheduler`
- `NotificationOutboxDispatcher`

## 3. Retry Failure Handling
- Identify recurrent failures from execution logs.
- Verify provider readiness and credentials (without exposing secrets).
- Use retry/resend endpoints for targeted recovery.

## 4. Provider Failure Playbook
### Messaging
1. Check `/api/carepilot/messaging/providers/status`.
2. Validate environment keys and provider endpoint reachability.
3. Use `/test-send` endpoint.

### AI Providers
1. Check `/api/ai/status`.
2. Verify provider enablement and API key presence.
3. Review `ai_invocation_logs` for error codes and latency.

### Voice Providers
1. Verify AI calls provider configuration and fallback settings.
2. Inspect execution events and failover markers.

## 5. Webhook Troubleshooting
- Validate verify token/secret configurations.
- Confirm callback payload shape and provider_call_id correlation.
- Unknown IDs should log warnings without crashing orchestration.

## 6. AI Calls Queue Troubleshooting
- List executions with status filters (`QUEUED`, `FAILED`, `ESCALATED`).
- For blocked queue:
  - check quiet-hours / suppression reasons
  - inspect throttling counters and scheduler health
- Use reschedule/suppress/cancel as containment actions.

## 7. Tenant Onboarding
- Create tenant via platform API.
- Assign plan/modules.
- Create tenant admin user.
- Configure clinic profile and users/roles.

## 8. Backup and Recovery
- PostgreSQL backup schedule and restore drills.
- MinIO object backup policy.
- Keycloak realm export/backup retention.

## 9. Incident Categories
- P1: authentication outage, cross-tenant leakage, mass message failure.
- P2: scheduler backlog growth, webhook ingestion degradation.
- P3: isolated provider/test-send failures.

## 10. Escalation Procedure
1. Identify impacted tenant(s) and module(s).
2. Capture correlation/request IDs and execution IDs.
3. Apply mitigation (disable campaign, suppress queue, switch provider).
4. Escalate to platform admin/on-call with artifacts and timeline.

