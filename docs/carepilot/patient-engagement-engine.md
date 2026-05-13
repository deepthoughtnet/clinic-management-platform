# CarePilot Patient Engagement Engine v1

## Overview
Patient Engagement Engine v1 adds a rule-based engagement layer on top of existing patient, appointment, consultation, prescription, billing, vaccination, and CarePilot execution data.

The V1 design is additive and low-risk:
- no ML model dependency
- no campaign execution flow change
- no schema migration required
- tenant-scoped computation

## Scoring Strategy
The service computes a `0..100` score per active patient.

Starting score: `100`

Negative signals:
- long inactivity (`carepilot.engagement.inactive-days`, default `90`)
- no-shows (`carepilot.engagement.high-risk-no-show-count`, default `2`)
- overdue unpaid/partially-paid/issued bills (`carepilot.engagement.overdue-bill-days`, default `3`)
- overdue vaccination due date
- pending refill risk
- missed follow-up risk

Positive signals:
- recent visit in last 30 days
- repeated completed appointments
- no major overdue risk signals

Level mapping:
- `80+` -> `HIGH`
- `50-79` -> `MEDIUM`
- `25-49` -> `LOW`
- `0-24` -> `CRITICAL`

## Cohorts
The API supports these cohorts:
- `HIGH_RISK_PATIENTS`
- `INACTIVE_PATIENTS`
- `HIGH_NO_SHOW_RISK`
- `OVERDUE_BILL_PATIENTS`
- `REFILL_RISK_PATIENTS`
- `VACCINATION_OVERDUE_PATIENTS`
- `HIGH_ENGAGEMENT_PATIENTS`
- `LOW_ENGAGEMENT_PATIENTS`
- `FOLLOW_UP_OVERDUE_PATIENTS`

## APIs
- `GET /api/carepilot/engagement/overview`
- `GET /api/carepilot/engagement/cohorts?cohort=...&offset=0&limit=50`
- `GET /api/carepilot/engagement/high-risk`
- `GET /api/carepilot/engagement/inactive`

RBAC:
- allowed: `CLINIC_ADMIN`, `AUDITOR`, tenant-scoped `PLATFORM_ADMIN`, `PLATFORM_TENANT_SUPPORT`
- denied by policy: `DOCTOR`, `BILLING_USER`, `RECEPTIONIST`

## Scheduler / Refresh
V1 uses on-demand computation per API request to avoid background recomputation risk and keep rollout additive.

## Future Extensibility
Planned next steps:
- optional scheduled materialization/caching
- trend snapshots
- campaign response feedback loop
- AI-assisted targeting recommendations
- richer refill and follow-up inference

## Limitations
V1 is deterministic and data-quality dependent. It does not include predictive ML and does not infer intent beyond existing operational fields.
