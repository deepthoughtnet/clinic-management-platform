# Jeevanam Automation Candidates

This backlog is derived from the currently implemented workflows and the existing automated tests in the repository.

## Buckets

- **Already automated**: existing tests in repo already cover the scenario well enough for a baseline.
- **P0 automate immediately**: release blocker / patient-safety / cross-tenant / publication-boundary coverage.
- **P1 automate before next major release**: major business workflow regression coverage.
- **P2 useful regression automation**: valuable but not release blocking.
- **Manual-only**: hard to automate reliably or dependent on runtime data that is better checked manually.

## Already automated

| Test ID | Scenario | Recommended framework/layer | Reason | Dependencies | Data isolation | Complexity |
|---|---|---|---|---|---|---|
| AUTH-001 | Auth bootstrap / session restore | UI unit / Node test | Existing auth bootstrap tests already exercise session logic | Auth store | Low | S |
| CONS-001 | Consultation workspace rendering and completion | UI unit + backend service tests | Existing consultation workspace and completion guard tests cover core behavior | Tenant fixture | Medium | M |
| AI-001 | Clinical reasoning persistence | Backend unit / integration | Current tests already cover reasoning persistence and parsing | Consultation fixture | Medium | M |
| MEDSAFE-001 | Medication safety deterministic blocking | Backend unit / integration | Existing engine/review tests already validate rule behavior | Prescription fixture | Medium | M |
| LAB-001 | Lab order lifecycle basics | Backend integration | Existing lab tests cover order, sample, result, publish | Lab fixture | Medium | M |
| PHARM-001 | Pharmacy procurement and reconciliation | Backend integration | Existing pharmacy service tests cover procurement and stock movements | Inventory fixture | Medium | M |
| DISC-001 | Provider workspace and provider publication UI behavior | UI regression | Existing Discover tests already cover workspace/editor/review basics | Provider fixture | Medium | M |
| PLATFORM-001 | Product implementation dashboard | UI snapshot/state test | Existing page test already validates the evidence-based dashboard shell | None | Low | S |

## P0 automate immediately

| Test ID | Scenario | Recommended framework/layer | Reason | Dependencies | Data isolation | Complexity |
|---|---|---|---|---|---|---|
| DISC-010 | Provider public profile draft/public isolation | Backend integration + Playwright | Critical publication boundary | Provider/hospital fixtures | High | L |
| DISC-011 | Submitted preview reads immutable submitted snapshot | Backend integration + Playwright | Prevents draft leakage during review | Submitted profile fixture | High | L |
| DISC-012 | Approved version publish promotion is atomic | Backend integration | Protects live profile continuity | Approved submission fixture | High | M |
| DISC-013 | Hospital doctor draft/public association isolation | Backend integration + Playwright | Prevents public association leakage | Hospital provider fixture | High | L |
| DISC-014 | Platform Admin review queue loads in platform mode | UI + backend integration | Must work without tenant selection | Platform admin fixture | Medium | M |
| PLATFORM-010 | Provider Connections tabs load lazily and correctly | UI regression | Prevents broad workspace regressions | Platform admin fixture | Medium | M |
| SECURITY-001 | Cross-tenant access denied | Backend security integration | Core isolation guarantee | Multiple tenant fixtures | High | M |
| SEC-002 | Public endpoints never expose draft state | API integration | Protects public Discover | Draft/public fixtures | High | M |
| SEC-003 | Terminal publication is not treated as active review | Backend + UI | Prevents stale review state regressions | Published review fixture | Medium | M |
| PROD-SMOKE-001 | Public landing / login / dashboard / provider workspace | UI smoke | Broad deployment sanity | Stable seed data | Low | S |

## P1 automate before next major release

| Test ID | Scenario | Recommended framework/layer | Reason | Dependencies | Data isolation | Complexity |
|---|---|---|---|---|---|---|
| PAT-001 | Patient registration duplicate handling | Backend + UI | High-volume operational path | Patient fixture | Medium | M |
| APT-001 | Appointment create/reschedule/cancel | Backend + UI | Core operational workflow | Tenant fixture | Medium | M |
| REC-001 | Reception check-in / queue lifecycle | UI + backend | Daily clinic workflow | Patient/appointment fixture | Medium | M |
| CONS-002 | Consultation update + completion guard | Backend integration | Patient safety and workflow integrity | Consultation fixture | Medium | M |
| AI-002 | AI-disabled consultation flow still usable | UI + backend | Important fallback | Consultation fixture | Medium | M |
| RX-001 | Prescription draft/preview/finalize | Backend + UI | Core clinical workflow | Consultation fixture | Medium | M |
| LAB-002 | Lab sample receive / reject / collect / verify | Backend + UI | Core lab workflow | Lab order fixture | Medium | M |
| BILL-001 | Bill / receipt / payment lifecycle | Backend + UI | Major finance workflow | Billing fixture | Medium | M |
| ENGAGE-001 | Campaign maker-checker / approval / activation | Backend + UI | Major CRM workflow | Campaign fixture | Medium | M |
| DISC-020 | Discover search/filter and public detail routing | UI regression | Public UX stability | Public profile fixtures | Low | M |
| CONNECT-020 | Provider application review publish flow | Backend + UI | Discover provider acquisition lifecycle | Provider application fixture | High | L |
| CARE-001 | Patient portal login / dashboard / appointments | UI + backend | Care handoff and patient support | Patient fixture | Medium | M |
| PLATFORM-020 | Platform Admin public profile review actions | UI + backend | Moderation workflow | Review fixture | High | L |

## P2 useful regression automation

| Test ID | Scenario | Recommended framework/layer | Reason | Dependencies | Data isolation | Complexity |
|---|---|---|---|---|---|---|
| NOTIF-001 | Notification center filtering and open/read state | UI + backend | Helpful operational regression coverage | Notification fixtures | Medium | S |
| PHARM-002 | Pharmacy POS / stock movement screens | UI regression | Good operator coverage | Pharmacy fixtures | Medium | M |
| PHARM-003 | FEFO / batch expiry checks | Backend unit | Important inventory correctness | Stock fixtures | Medium | M |
| ENGAGE-002 | Reminder and delivery attempts UI | UI + backend | High support value | Campaign/reminder fixture | Medium | M |
| DISC-021 | Public profile media rendering edge cases | UI regression | Helps prevent image regressions | Media fixtures | Medium | M |
| PLATFORM-021 | Product implementation dashboard text refresh | UI snapshot | Documentation of release posture | None | Low | S |
| SEC-010 | Direct URL access and refresh behaviour for protected routes | UI regression | Good security regression | Auth fixtures | Medium | M |
| CARE-010 | Patient care landing / login page flows | UI regression | Low-risk guardrails | Patient auth fixture | Low | S |

## Manual-only

| Test ID | Scenario | Recommended framework/layer | Reason | Dependencies | Data isolation | Complexity |
|---|---|---|---|---|---|---|
| OPS-001 | Production backup / restore drill | Manual + ops runbook | Environment-specific and destructive | Production-like infra | High | L |
| OPS-002 | Production monitoring / incident response drill | Manual + ops runbook | Operational process rather than product code | Monitoring stack | High | L |
| OPS-003 | Security / penetration review | Manual security assessment | External validation required | Security team | High | L |
| AI-900 | Clinical qualitative model-quality review | Manual clinical review | Human judgment required | AI runtime | Medium | L |

