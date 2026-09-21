# Jeevanam Healthcare Platform — End-to-End Current-State Assessment

**Assessment date:** 2026-09-19 UTC

**Repository:** `clinic-management-platform`

**Assessed branch / commit:** `main` / `20061064012cd055afb3b0a0a0bfc9a2a129767a`

**Known production baseline:** `jeevanam-prod-2026-09-05-01` / `6f79bc84f0e3979aac0bc4cbbc3393776f2e490b`

**Assessment mode:** static repository analysis plus safe local builds/tests; no deployment, service restart, database mutation, production query, or paid provider call.

This document supersedes the previous AIVA-only content of `analysis.md`. Still-valid AIVA findings from that audit are retained in Sections 6, 13, and 14 and were rechecked against the current source.

## Evidence convention

- **VERIFIED** — directly observed in source, configuration, migrations, Git metadata, or a command run during this assessment.
- **INFERRED** — conclusion supported by multiple repository facts but not proven against a running production/UAT system.
- **UNVERIFIED** — requires runtime, operational, provider, or production data not available from the repository.

Status values in this report use only the required classification set:

- `COMPLETE`
- `COMPLETE - NEEDS HARDENING`
- `IMPLEMENTED - NEEDS UAT`
- `PARTIAL`
- `SCAFFOLDED`
- `NOT IMPLEMENTED`
- `LEGACY / DEPRECATED`
- `UNKNOWN - INSUFFICIENT EVIDENCE`

---

# 1. Executive Summary

Jeevanam is a broad, functioning modular-monolith healthcare product rather than a prototype. The repository contains real persisted workflows for tenant administration, appointments and queues, consultations, prescriptions, medication safety, clinical reasoning, documents and longitudinal findings, laboratory, pharmacy, vaccination, billing, engagement, public discovery, patient care, provider onboarding, commercial entitlements, platform administration, operational telemetry, and voice. There are 96 REST controllers, 168 Flyway migrations, four React applications, a 48-project backend reactor, provider adapters, production Docker Compose, backup automation, and substantial Java and source-contract test suites.

The repository is not currently a release candidate. The current HEAD is seven commits and 275 files beyond the last known production release, the working tree contains uncommitted AIVA V2 and Care UI changes, the ordinary backend test run is red on a date-expired Engage test, and `web-admin` is red on 3 of 138 tests. No Playwright/browser end-to-end suite exists. The backend packages successfully with tests skipped, and all four frontends build, but buildability is not the same as release readiness.

The most serious production concerns are fail-open production defaults for patient/provider development OTP exposure, PHI/PII and raw AI payload logging at INFO/WARN, webhook signature checks that become optional when secrets are blank, incomplete restore/DR coverage, and lack of a clean green release gate. Platform Ops Phase 1A is useful but incomplete: API, DB, Keycloak, Redis, MinIO, AI activity, Document AI activity, scheduler state, release metadata, alert/DLQ surfaces are present; backup status is explicitly `UNKNOWN`, and container/server/SSL/user-activity/incident telemetry is absent.

Architecture has drifted materially from the repository constitution. `api-bff` contains 28 JPA entities, 27 Spring Data repositories, and 13 forbidden persistence-style package directories. The architecture validation script passes because it checks only Commercial Catalog boundaries, not the constitution-wide rules. Laboratory and some clinical/document features are split between domain modules and BFF-owned persistence, increasing duplication and migration risk.

### Evidence-based rounded estimates

| Measure | Estimate | Basis |
|---|---:|---|
| Overall functional completion | **~75%** | Most named ambulatory workflows have UI, APIs, persistence, validation, and lifecycle code. Deductions are for incomplete finance depth, operational monitoring, consent/privacy maturity, IPD/insurance, voice hardening, split/duplicate implementations, and flows that remain UAT-only. |
| Production readiness | **~50%** | Build/deploy/backup foundations exist, but security defaults, sensitive logging, incomplete DR, floating images, missing resource limits, incomplete telemetry, red tests, and undeployed/dirty release state materially reduce readiness. |
| UAT readiness | **~55%** | Broad unit/service/source-contract coverage exists, but no browser E2E suite, incomplete role/tenant negative matrix, weak Lab/Inventory domain coverage, provider/runtime tests not fully executable, and current red tests prevent a UAT-ready baseline. |
| Documentation readiness | **~75%** | Architecture docs, many approved specs, and operations runbooks exist. Many specs describe approval or intent rather than verified delivery; restore, deployment verification, and implementation-status accuracy lag current code. |

These are deliberately rounded estimates, not measurements to single-point precision. The weighting gives the largest share to working clinic and clinical flows, then security/data integrity, then operational/release proof.

### Immediate conclusion

Do not tag or deploy current HEAD. First establish a clean, reproducible release branch; disable unsafe production defaults; remove sensitive payload logging; close webhook authentication and restore gaps; then execute role- and tenant-based browser UAT on the actual deployment topology.

---

# 2. Current Git / Release State

## 2.1 Current state

| Item | Finding |
|---|---|
| Branch | **VERIFIED:** `main`, tracking `origin/main`. |
| HEAD | **VERIFIED:** `20061064012cd055afb3b0a0a0bfc9a2a129767a`, subject `fix AIVA V2 cross-tenant reschedule`. |
| HEAD tag | **VERIFIED:** `aiva-v2-cross-tenant-reschedule-2026-09-19` resolves to current HEAD. It is a feature/baseline tag, not a production tag. |
| Last known production tag | **VERIFIED:** `jeevanam-prod-2026-09-05-01` resolves to `6f79bc84f0e3979aac0bc4cbbc3393776f2e490b`. |
| Delta | **VERIFIED:** 7 commits; 275 files; approximately +37,408 / -1,290 lines. |
| Deployment state | **UNVERIFIED:** the repository proves the 2026-09-05 baseline was tagged; it does not prove any later commit was deployed. Treat later commits as not production-confirmed. |
| Local changes | **VERIFIED at final consistency pass:** five modified files and one untracked test, all in AIVA V2/Care session-reset work. |

Local changes present during the final pass:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/aivav2/AivaV2ConversationService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/aivav2/AivaV2Models.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/aivav2/AivaV2SessionStore.java`
- `web-care/src/api/patientPortal.ts`
- `web-care/src/pages/patient/PatientPortalPages.tsx`
- untracked `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/patientportal/aivav2/AivaV2SessionStoreTest.java`

The working tree changed while the assessment was running: the initial inventory showed three AIVA files; the final consistency pass showed the two additional Care/AIVA files above. Assessment commands did not edit these source files. This means the snapshot was actively changing and is unsuitable as a release evidence baseline.

## 2.2 Release delta

| Commit | Module / change | Risk | Test evidence | Deployed? |
|---|---|---|---|---|
| `1f80d958` | Production MinIO backup compatibility; removed dependence on missing `awk`/`sed` in `minio/mc:latest`. | Medium: production recovery path. | User-supplied operational evidence says DB, Keycloak DB, and MinIO backup completed after fix; no automated restore test. | **UNVERIFIED** after known production tag. |
| `d954c57f` | Platform Ops Phase 1A, release metadata, AI/Document/scheduler probes, ElevenLabs/voice changes, Care lab UI. | High: ops and voice runtime. | Unit/source-contract tests exist; Platform Ops test passed in prior module reports, but full current suite is red. | **UNVERIFIED**. |
| `5dbdf839` | Care unified discovery, clinic context, availability, patient access changes. | Medium/high: patient booking and privacy context. | `web-care` 22/22 passed; backend service/controller tests exist. No browser E2E. | **UNVERIFIED**. |
| `058bfbd9` | Large AIVA V2 English workflow introduction plus major legacy Care AI expansion. | High: appointment writes, new dual runtime, very large change. | Extensive AIVA unit/certification tests exist; V2 disabled by default; full current release suite not green. | **UNVERIFIED**. |
| `f2f9ac5c` | AIVA V2 multilingual typed-text boundary and rendering. | High: language/state/confirmation semantics. | Focused language and transactional tests exist. No real-provider/browser UAT evidence. | **UNVERIFIED**. |
| `50a9708b` | AIVA cancellation confirmation/fresh-time persistence refinements. | High: destructive appointment action. | Cancellation persistence/integration tests added; current whole suite not green. | **UNVERIFIED**. |
| `20061064` | Cross-tenant reschedule fix. | High: tenant isolation and appointment mutation. | Cross-tenant/reschedule tests added; uncommitted session work now sits on top. | **UNVERIFIED**. |

## 2.3 Release metadata quality

- Production Compose supports `JEEVANAM_RELEASE_TAG`, Git commit, build timestamp, deployment timestamp, environment, and API version injection.
- Platform Ops renders those values and warns when incomplete.
- `scripts/build-all.sh` builds backend, `web-admin`, and `web-care` only; it omits `web-discover` and `web-aiva`.
- `scripts/smoke-test.sh` checks API, Admin, Care, Keycloak, PostgreSQL, and API health. It omits Redis, MinIO, Discover, AIVA, voice, external AI/OCR providers, authorization, and critical user flows.

---

# 3. Architecture Summary

## 3.1 Component map

| Component | Responsibility | Main dependencies | Status |
|---|---|---|---|
| `backend/api/api-bff` | REST/GraphQL/WebSocket inbound adapter, DTO mapping, orchestration; currently also owns substantial persistence and business logic. | All domains/platform/providers, Spring Boot, PostgreSQL, Redis, MinIO, Keycloak, AI/OCR/voice providers. | **PARTIAL** architecture conformance; functionally broad. |
| Domain modules | Identity, clinic, patient, appointment, consultation, prescription, inventory, billing, notification, AI, laboratory, vaccination, Engage, Commercial, Discover. | Platform contracts/audit/events/security plus permitted domain dependencies. | **IMPLEMENTED - NEEDS UAT** overall; coverage varies by module. |
| Platform modules | Core, audit, outbox, Spring context, business events, contracts, security, storage SPI, provider integration. | Spring/JPA and domain-facing contracts. | **COMPLETE - NEEDS HARDENING** except empty modules. |
| `platform-idempotency` / `platform-jobs` | Intended shared platform foundations. | None in source. | **SCAFFOLDED**: zero main/test files. |
| AI providers | Gemini, Groq, Sarvam LLM plus common SPI and routing/fallback. | External HTTP providers, AI domain. | **COMPLETE - NEEDS HARDENING**; live-provider UAT and log safety remain. |
| OCR | Tesseract adapter and OCR SPI. | Local Tesseract executable/PDF rendering. | **IMPLEMENTED - NEEDS UAT**. |
| Messaging/notification providers | SMTP, generic SMS, Meta WhatsApp, logging/no-op-style providers. | External SMTP/HTTP APIs and notification/Engage domains. | **IMPLEMENTED - NEEDS UAT**; production provider enablement is configuration-dependent. |
| MinIO storage provider | Object put/get/delete/presign and optional bucket creation. | MinIO SDK and one configured bucket. | **COMPLETE - NEEDS HARDENING**. |
| `web-admin` | Clinic, clinical, pharmacy, laboratory, Engage, platform/admin UI. | API BFF, Keycloak, React/MUI. | **IMPLEMENTED - NEEDS UAT**; 3 tests currently fail; 4.5 MB minified main chunk. |
| `web-care` | Patient login/access, dashboard, discovery, appointments, reports, prescriptions, bills, notifications, AIVA/voice. | Patient/public APIs and patient sessions. | **IMPLEMENTED - NEEDS UAT**. |
| `web-discover` | Public directory/search/profile/SEO-like landing pages and provider workspace. | Public/provider APIs and provider sessions. | **IMPLEMENTED - NEEDS UAT**; SPA limits true SEO. |
| `web-aiva` | AIVA marketing/product shell linking to Care runtime. | Static React app and Care URL. | **PARTIAL**; not the operational assistant itself and has no tests. |
| PostgreSQL/Flyway | System of record and schema evolution. | 168 ordered migrations. | **COMPLETE - NEEDS HARDENING**; live applied state unverified. |
| Keycloak | Workforce identity/realm roles/client; service client for provisioning. | Keycloak 24 and BFF JWT validation/admin client. | **COMPLETE - NEEDS HARDENING**. |
| Redis | Cache/session/coordination dependency and health probe. | Redis 7. | **IMPLEMENTED - NEEDS UAT**; outage semantics and compose readiness are weak. |
| Schedulers/events/DLQ | Reminders, AI jobs, no-show reconciliation, Engage execution, business event retry/dead-letter. | PostgreSQL, in-process schedulers, provider adapters. | **IMPLEMENTED - NEEDS UAT**. |
| Python realtime voice runtime | Production Compose voice gateway, WebSocket proxy/orchestration, Whisper/Piper integration. | FastAPI, Whisper, Piper, BFF upstream. | **PARTIAL**; tests cannot run from declared requirements. |
| Java realtime voice gateway | Persisted voice session/transcript/orchestration alternative. | JPA, voice provider SPIs. | **UNKNOWN - INSUFFICIENT EVIDENCE** as a runtime: built by Maven but not selected by production Compose. |
| Docker Compose / scripts | Local, UAT, production topology; build/smoke/DB/backup automation. | Docker, systemd, shell tooling. | **PARTIAL** production hardening. |

## 3.2 Placement/dependency assessment

Owning bounded contexts are mostly clear in module names, but placement no longer matches the constitution:

- **VERIFIED:** `api-bff` has 28 `@Entity` classes, 27 Spring Data repository interfaces, and persistence packages under Clinical Documents, Clinical Intake, Clinical Memory, Help, Inventory, Laboratory, Medication Safety, Ops, Pharmacy, Prescription Template, and Reliability.
- **VERIFIED:** repository rules explicitly forbid those constructs in `api-bff`.
- **VERIFIED:** `scripts/validate-architecture.sh` passes but checks only Commercial Catalog package imports. It does not detect the above violations.
- **INFERRED:** Laboratory, Pharmacy, documents, longitudinal memory, ops, and prescription safety cannot be evolved safely until ownership is reconciled; persistence is split between domain modules and the BFF.

## 3.3 Frontend architecture

- `web-admin` defines 82 routes, Care 26, and Discover 53.
- Route and navigation gating are implemented, but the backend remains the real authorization boundary.
- Two platform routes, `/platform/tenants/:tenantId` and `/platform/plans`, lack `PlatformAdminGate`; the corresponding `/api/platform` controller is correctly protected with `PLATFORM_ADMIN`. This is a frontend visibility/UX defect, not a verified backend privilege escalation.
- The apps use many Node source-contract tests that inspect source strings. These catch regressions cheaply but do not prove DOM behavior, accessibility, routing, network integration, or authorization outcomes.

---

# 4. Module Completion Matrix

| Area | Status | Summary |
|---|---|---|
| A. Platform / Tenant Administration | **IMPLEMENTED - NEEDS UAT** | Tenant lifecycle, profile, users, roles, departments, doctor setup, modules, and Commercial surfaces exist; entitlement authority is dual and cross-tenant proof is incomplete. |
| B. Reception / Front Desk | **COMPLETE - NEEDS HARDENING** | Registration, scheduling, availability, day board, check-in/queue/token and lifecycle code are broad and well tested at service level; no browser UAT suite. |
| C. Doctor Workspace | **IMPLEMENTED - NEEDS UAT** | Full consultation/prescription/lab/document UI and APIs exist; domain test depth is low and reopen/review behavior lacks end-to-end proof. |
| D. Doctor AI / Clinical Reasoning | **COMPLETE - NEEDS HARDENING** | Provider routing, entitlement/permission, grounding, red flags, suggestions, safety, persistence, stale-context hashing, audit and manual fallback are implemented; PHI logging and live-provider UAT block production confidence. |
| E. Longitudinal Intelligence | **IMPLEMENTED - NEEDS UAT** | Structured concepts, review decisions, trusted/rejected findings, current meds/latest labs/trends/context exist; multi-report correctness needs clinical UAT. |
| F. Clinical Documents / Document AI | **IMPLEMENTED - NEEDS UAT** | Upload, MinIO, OCR, AI extraction, jobs/retries, review/promotion/audit exist; file safety, retention, provider/document-type UAT remain. |
| G. Laboratory | **IMPLEMENTED - NEEDS UAT** | Configuration, order/sample/result/verification/publication/delivery and doctor/patient visibility exist; persistence ownership is split and domain tests are thin. |
| H. Pharmacy | **IMPLEMENTED - NEEDS UAT** | Master, inventory, batches, procurement, stock, dispensing, POS, returns and reconciliation surfaces exist; duplicate UIs and near-zero domain tests reduce confidence. |
| I. Vaccination | **COMPLETE - NEEDS HARDENING** | Master, recording, schedule/recommendation, duplicate controls, history, documents, CSV and RBAC exist with meaningful tests; browser/role UAT remains. |
| J. Billing / Finance | **PARTIAL** | Bills, service lines, payments, receipts, refunds, discounts and ledgers exist; settlement, reconciliation, tax depth, insurance/TPA, credit/debit accounting and finance reporting are incomplete. |
| K. Engage / CRM | **IMPLEMENTED - NEEDS UAT** | Leads, campaigns, reminders, maker-checker, schedulers, execution/retry/DLQ, provider status and dashboards exist; providers default disabled and dated tests are red. |
| L. Discover | **IMPLEMENTED - NEEDS UAT** | Search, location, profiles, directories, no-result behavior, onboarding/publication and booking handoff exist; true server-rendered SEO and browser/mobile proof are missing. |
| M. Care / Patient Portal | **IMPLEMENTED - NEEDS UAT** | Controlled access/login, appointments, discovery, reports, prescriptions, bills, notifications, profile and AIVA exist; unsafe production OTP defaults and no browser privacy tests. |
| N. Provider Onboarding / Access | **IMPLEMENTED - NEEDS UAT** | Registration, access requests, phone/session login, workspace, approval, ownership/claim and publication lifecycle exist; production identity and business validation need UAT. |
| O. Platform Admin | **PARTIAL** | Tenant/provider/AI/Commercial/Ops consoles exist; some platform features remain legacy-authoritative, route visibility is inconsistent, and support tooling is incomplete. |
| P. Platform Ops / Observability | **PARTIAL** | Core dependency probes, scheduler, queues, provider metrics, alerts, DLQ, release metadata exist; backup, host/container, SSL, user/activity, incident and broad integration telemetry are missing. |
| Q. Voice / Realtime AI | **PARTIAL** | Patient voice flow, WebSockets, STT/TTS fallbacks, Whisper/Faster-Whisper/Piper/ElevenLabs/Sarvam adapters and health checks exist; duplicate runtimes and absent deploy-level latency/UAT proof remain. |
| R. Security / Compliance / Audit | **PARTIAL** | Strong method authorization/audit foundations exist, but sensitive logs, unsafe defaults, optional webhook signatures, public actuator surface, file scanning/retention, and incomplete tenant-negative testing are material gaps. |
| Commercial entitlement runtime cutover | **PARTIAL** | Commercial S1–S5 structures and diagnostics exist; UI explicitly says legacy tenant access remains authoritative until cutover. |
| Insurance / TPA | **NOT IMPLEMENTED** | “Insurance” exists mainly as payment mode, document type, or public profile content; no payer/claim/preauth/adjudication lifecycle. |
| IPD / bed/ward/admission | **NOT IMPLEMENTED** | No admission, bed, ward, inpatient medication, discharge, or nursing domain found. |
| `platform-idempotency` / `platform-jobs` | **SCAFFOLDED** | Empty Maven modules. |

---

# 5. Detailed Module Findings

## A. Platform / Tenant Administration — IMPLEMENTED - NEEDS UAT

**Evidence:** `PlatformTenantController` and `PlatformTenantService`; identity/clinic domain entities and services; tenant/user/role/profile/department/doctor settings pages; Keycloak provisioning; module/plan APIs; Commercial Catalog, subscriptions, overrides, effective-entitlement snapshots and runtime-diff pages.

**Implemented:** tenant create/activate/deactivate, plan and module updates, admin-user provisioning, clinic profile/branding, departments, doctor configuration, memberships, role/permission metadata, tenant-scoped request context, audit details, Commercial plan/catalog/subscription models.

**Missing/risks:** Commercial runtime is disabled by default and legacy module entitlements remain authoritative; `ADMIN` is still a compatibility alias for tenant-admin permissions; two platform UI routes lack the platform gate; no comprehensive cross-tenant matrix exists; Keycloak realm export does not itself prove production realm parity.

**Next:** complete Commercial cutover policy, remove route-gate inconsistencies, certify tenant isolation using two tenants and every administrative role, and compare the deployed realm/client to `local/keycloak/realm-export.json` without exposing secrets.

## B. Reception / Front Desk — COMPLETE - NEEDS HARDENING

**Evidence:** appointment domain (46 main files, 6 test files, 62 tests observed passing), appointment/day-board/queue APIs and Admin pages, patient quick registration, doctor availability/unavailability, slot search, waitlist, token/queue state, no-show reconciliation scheduler.

**Implemented:** registration, booking, rescheduling, cancellation, check-in, queue/token sequencing, availability, day board, payment-bypass metadata, waitlist and no-show lifecycle.

**Missing/risks:** no real browser workflow test, concurrent booking race proof is incomplete, and one Admin doctor-availability source-contract test currently fails.

**Next:** Playwright UAT for receptionist flows, concurrency tests for slot/token assignment, and negative authorization tests for reception versus clinical/finance roles.

## C. Doctor Workspace — IMPLEMENTED - NEEDS UAT

**Evidence:** consultation/prescription domains, `ConsultationController`, SOAP/AI summary services, consultation completion guard, clinical intake, investigations/lab mapping, prescription versioning/documents, and the large `ConsultationWorkspacePage`.

**Implemented:** patient selection/context, complaints/history/vitals, diagnosis and notes, SOAP draft, prescriptions, investigations, follow-up, completion guards, document generation, consultation history and correction/finalized prescription behavior.

**Missing/risks:** consultation domain has only two test files and its direct observed unit suite was primarily vitals; much business logic sits in BFF services/UI; the Admin prescription-intelligence contract test is red; reopen/review and role handoff lack end-to-end proof.

**Next:** clinician UAT around complete/reopen/correction, prescription immutability, lab handoff, and simultaneous updates; add API integration tests independent of source-string assertions.

## D. Doctor AI / AIVA / Clinical Reasoning — COMPLETE - NEEDS HARDENING

**Evidence:** AI domain (99 main/15 test files), Gemini/Groq/Sarvam adapters, `ClinicalReasoning*`, `AiDoctorCopilotService`, consultation draft/ask services, medication safety services, persisted reasoning/suggestion/safety entities, AI Ops, controller security tests, context hashes and stale-review tests.

**Implemented:** provider chain/fallback, models/timeouts/tokens, AI permissions and AI_COPILOT module gating, doctor toggle UI, clinical context grounding, red flags/differentials/explanations/history-gap questions/test suggestions, prescription suggestions, medication safety coverage and acknowledgement, accept/edit/reject persistence, context hash/stale status, invocation audit, truncation handling, disclaimers, and failure isolation from manual consultation.

**Missing/risks:** raw/normalized model output and clinical previews are logged; live-provider failure/quota/truncation certification is absent; AI_COPILOT Commercial versus legacy entitlement authority is transitional; no clinical safety benchmark set is versioned; no systematic hallucination/adversarial UAT.

**Next:** redact model/clinical logs, establish golden clinical evaluations and provider-outage tests, certify manual workflow under all AI failures, and define model-change approval/rollback.

## E. Longitudinal Intelligence — IMPLEMENTED - NEEDS UAT

**Evidence:** `PatientLongitudinalConceptEntity/Repository`, `PatientLongitudinalMemoryService`, finding review service, longitudinal context builder, Patient Intelligence UI, repository/service tests, migrations through V168.

**Implemented:** pending/trusted/rejected findings, source links, finding-level review, corrected values, current medication/lab/risk/condition group projections, trends, consultation/document sources, rejected-finding exclusion, and patient/tenant-aware reads.

**Missing/risks:** clinical correctness across multiple conflicting reports is not proven; some AI-reviewed content is also appended to free-text patient notes, creating duplicate authority; child-record scoping depends on parent joins in places; long-term deduplication/promotion semantics require clinical UAT.

**Next:** multi-report conflict/rejection/correction UAT, remove free-text duplication as authority, and add explicit cross-patient/cross-tenant negative tests.

## F. Clinical Documents / Document AI — IMPLEMENTED - NEEDS UAT

**Evidence:** Clinical Document controller/service/entity, MinIO provider, OCR SPI/Tesseract, extraction jobs/processor/retries, deterministic lab parser, AI response adapter, review/promotion services, authorization tests, download/timeline/review tests.

**Implemented:** upload, 25 MB limit, PDF/JPG/JPEG/PNG/WEBP allowlist, filename sanitation, extension/content-type consistency, checksum, tenant/patient/document object keys, presigned download, OCR, classification/extraction, retry/job status, review/approve/reject/correct, audit and longitudinal promotion.

**Missing/risks:** client-supplied MIME/extension is not equivalent to magic-byte validation; no antivirus/malware scan; no explicit object encryption/versioning/retention policy; soft-deleted metadata does not establish object retention/deletion policy; Document AI Ops health is activity-derived rather than a provider readiness check; report-type correctness needs a representative corpus.

**Next:** add file signature and malware scanning, formalize PHI encryption/retention/access logging, and certify formats/report classes/failure/retry behavior on a de-identified corpus.

## G. Laboratory — IMPLEMENTED - NEEDS UAT

**Evidence:** lab master/category/parameter entities, direct/consultation orders, samples, results, verification, payment, report publication/delivery/public verification, report artifacts/revisions and Lab UI tests.

**Implemented:** master configuration/CSV, ordering, accession/order references, specimen collection/receive/reject, technician result entry, partial results, verifier workflow, reference ranges/flags, PDF/public verification, doctor review and patient visibility, RBAC and audit details.

**Missing/risks:** core lab persistence is in BFF while newer lifecycle/publication persistence is in `laboratory-domain`; the domain has one test file; no full specimen-to-publication browser test; analyzer interfaces and external LIS/HL7 are absent.

**Next:** reconcile ownership, add PostgreSQL lifecycle/integrity tests, and UAT partial/final/amended/critical result flows by technician, approver, doctor and patient roles.

## H. Pharmacy — IMPLEMENTED - NEEDS UAT

**Evidence:** inventory domain, Pharmacy Operations/POS controllers and services, medicine/stock/batch/transaction/supplier/PO/GRN/invoice/physical-count/reconciliation entities, dispensing and POS pages.

**Implemented:** medicine master/CSV, locations, stock and movement, batches/expiry, suppliers, purchase orders, goods receipts, supplier invoices, dispensing, prescription fulfillment, POS, returns, pricing/discounts, stock counts and reconciliation audit fields.

**Missing/risks:** inventory domain has no substantive Java test class despite 50 main files; Admin contains duplicate procurement/reconciliation pages and explicit test routes; some Pharmacy persistence still exists in BFF; accounting settlement is not integrated deeply.

**Next:** consolidate routes/pages, add database-backed inventory invariants and concurrent stock tests, then UAT procure→receive→dispense→return→reconcile with pharmacist and admin roles.

## I. Vaccination — COMPLETE - NEEDS HARDENING

**Evidence:** vaccine master/patient vaccination entities and services, recommendation service, AEFI, billing/document/passport/reminder APIs, CSV/UI tests, 26 observed passing domain tests.

**Implemented:** master, administration record, recommendations/schedule, required fields, role checks, duplicate prevention, history, billing/document generation and export/import.

**Missing/risks:** browser role matrix and adverse-event/reporting UAT are absent; national registry/inventory linkage is not present.

**Next:** run nurse/doctor/reception negative UAT and verify duplicate/concurrent administration, certificate/passport, and reminder flows.

## J. Billing / Finance — PARTIAL

**Evidence:** bills/lines, consultation fee state, payments, receipts, refunds, ledgers, discounts, invoice email/PDF and billing pages; 34 billing service tests passed and 5 PostgreSQL/Testcontainers repository tests were skipped because Docker was unavailable.

**Implemented:** consultation/service/lab/pharmacy-style bill lines, invoice/bill lifecycle, partial/multiple payments, receipts, refunds, discount type/value, payment/refund ledgers and outstanding context.

**Missing/risks:** no full general ledger, settlement/reconciliation, cash drawer/shift closing, tax jurisdiction engine, credit/debit notes, insurer/TPA claims, preauthorization, adjudication, payer settlement, or mature finance reporting. “Insurance” is only a payment mode/reference in core billing.

**Next:** define finance bounded-context roadmap and accounting invariants before expanding; keep insurance/IPD out of completion claims.

## K. Engage / CRM — IMPLEMENTED - NEEDS UAT

**Evidence:** CarePilot domain (147 main/27 test files), lead/campaign/template/reminder/AI-call/execution entities/services, maker-checker approvals, schedulers, retry/dead-letter, webhook delivery updates, Ops and analytics pages.

**Implemented:** leads, assignments/follow-ups, campaign audience/preview/approval/trigger, reminders, templates, execution states, delivery callbacks, retries/backoff, DLQ/replay, provider metrics and dashboards.

**Missing/risks:** `LeadServiceTest` and frontend lead validation use fixed September 2026 “future” dates and are now red; email/SMS/WhatsApp scheduler/providers default disabled; rate-limit values are validated but repository documentation states runtime enforcement is not implemented; patient-level consent/opt-out is less mature than tenant policy; unsigned callbacks are accepted if secrets are blank.

**Next:** fix time-controlled tests, enforce fail-closed callback secrets and runtime rate/consent policy, then conduct provider sandbox UAT including retries, duplicates, invalid signatures and opt-out.

## L. Discover — IMPLEMENTED - NEEDS UAT

**Evidence:** Discover domain (114 main/10 test files; 119 observed passing tests), public directory/profile/landing/provider ownership/moderation services, 53 UI routes, 30/30 frontend tests passing.

**Implemented:** doctor/clinic/hospital/service/speciality search, location selection/map hooks, no-result messaging, public metadata/profile/landing pages, provider ownership/claims/drafts/moderation/publication, clinic consent separation, booking handoff.

**Missing/risks:** React SPA is not a server-rendered SEO system; geolocation/browser/mobile/accessibility is not exercised in a real browser; demo/public card specs include proposed work; association/projection repair complexity creates eventual-consistency risk.

**Next:** run mobile/browser/SEO crawl UAT, validate anonymous privacy boundaries and indexing metadata, and monitor projection repair/publication consistency.

## M. Care / Patient Portal — IMPLEMENTED - NEEDS UAT

**Evidence:** patient session/OTP/access request services, Care dashboard/discovery/booking/reports/prescriptions/bills/notifications/profile/AIVA routes, 22/22 frontend tests passing, backend portal tests.

**Implemented:** controlled access and phone/session login, clinic authorization, booking/reschedule/cancel, public provider discovery, lab/document and prescription views, billing/notifications, patient profile and AIVA text/voice entry.

**Missing/risks:** production Compose defaults both patient and provider development OTP exposure to `true`; no browser privacy test proves that one patient/clinic cannot access another; patient session and CSRF/CORS threat model needs explicit review; local changes alter session-reset behavior.

**Next:** set production OTP fail-safe false with startup rejection, complete two-patient/two-clinic browser tests, and freeze/verify the current uncommitted session changes before release.

## N. Provider Onboarding / Access — IMPLEMENTED - NEEDS UAT

**Evidence:** provider registration/access/auth/workspace controllers, Discover onboarding/access/ownership domains, platform review pages and tests.

**Implemented:** doctor/clinic/hospital registration, access requests, provider session login, workspace/drafts, platform approval, ownership claims/disputes, tenant consent and publication lifecycle.

**Missing/risks:** production identity/provider-session lifecycle has no browser UAT; business verification is workflow-based rather than an external credentialing integration; dev OTP default is unsafe; operational support/audit review needs certification.

**Next:** provider sandbox UAT from application through approval/login/publication/revocation/dispute with negative cases.

## O. Platform Admin — PARTIAL

**Evidence:** platform tenant/provider/public-profile/Commercial/AI Ops/Platform Ops/help pages and backend `PLATFORM_ADMIN` controls.

**Implemented:** tenant and user operations, provider reviews/connections, Commercial catalog/subscriptions/entitlements, AI usage/configuration views, operational alerts/DLQ/health, help content.

**Missing/risks:** platform plans/tenant detail UI gates are inconsistent; Commercial is not runtime-authoritative; support impersonation/break-glass/session revocation/user activity tooling is absent or not evidenced; several nav items are explicitly “coming soon.”

**Next:** define platform support privilege model, add immutable privileged-action audit, close UI gating, and finish runtime cutover/ops surfaces.

## P. Platform Ops / Production Observability — PARTIAL

**Evidence:** `PlatformOperationsOverviewService`, diagnostics controller/service/state store, `PlatformOpsService`, scheduler monitors, alerts and DLQ persistence, `PlatformOpsPage`, release properties and tests.

| Required signal | Actual state |
|---|---|
| API | Implemented: JVM/application uptime. |
| PostgreSQL | Implemented: `select 1`, schema report, Hikari pool data. |
| Keycloak | Implemented: realm discovery probe. |
| Redis | Implemented: ping/latency. |
| MinIO | Implemented: configured bucket existence. |
| Gemini/Groq/Sarvam | Implemented from recent invocation/readiness; manual diagnostics can invoke providers. |
| Document AI | Implemented as recent document activity/failure counts; no independent provider readiness in overview. |
| Scheduler/stuck jobs | Implemented partially: in-memory heartbeats/locks plus Engage queue/stuck detection. State resets on process restart. |
| Backups | **Not implemented:** hard-coded `UNKNOWN`, “Structured backup telemetry not available.” |
| Release metadata | Implemented. |
| Users/login/activity | Not implemented as a production dashboard signal. |
| Containers | Not implemented. |
| CPU/memory/disk | JVM runtime only; host/container resources not implemented. |
| SSL expiry | Not implemented. |
| External integrations | Provider metrics exist, but not a complete health registry. |
| Alerts/incidents | Rules, alerts, acknowledgement/resolution exist; no external paging or incident lifecycle evidenced. |
| DLQ/runtime | Implemented for relevant internal jobs/events. |

**Risks:** diagnostics for AI/Document AI can make external calls and MinIO diagnostics can write/read/delete a health object; those are privileged and should be rate/audit/cost controlled. Overview backup status is knowingly incomplete.

**Next:** implement Phase 1B using structured, non-secret collectors for backup age/result, Docker/host resources, TLS expiry, login/activity aggregates, integration status and incident/paging state.

## Q. Voice / Realtime AI — PARTIAL

**Evidence:** patient voice REST/WebSocket service, auth interceptors, STT/TTS provider adapters, voice orchestration and appointment workflow, Python runtime in production Compose, Whisper/Faster-Whisper/Piper services, Java realtime gateway and tests.

**Implemented:** patient-session WebSocket authentication, audio size/session/turn limits, heartbeat, STT fallbacks, LLM chain, TTS fallbacks, mute/session UI, health endpoints, latency fields, and a Python gateway proxying to the BFF Care AI WebSocket.

**Missing/risks:** production Compose deploys the Python runtime while Maven builds a separate Java gateway; ownership and target runtime are unclear. Python tests import `pytest`, but `pytest` is absent from `requirements.txt`, so tests could not run and would not run in the production image. Whisper uses `:main` and downloads a model at startup; Piper image/model setup also depends on external downloads. No measured latency/concurrency/failover UAT was found.

**Next:** choose one runtime authority, add a test/dev dependency lock, pin images/models/checksums, run load/latency/interruption UAT, and prove privacy/logging behavior for transcripts/audio.

## R. Security / Compliance / Audit — PARTIAL

**Evidence:** Spring method security, JWT issuer/timestamp validation, tenant-role filter, 567 `@PreAuthorize` annotations across 96 controllers, permission mappings, audit entities/services, object key scoping, and numerous controller security tests.

**Implemented strengths:** backend authorization is pervasive; `/api/platform` is platform-admin-only; patient/provider sessions use custom authentication filters; public endpoints are explicit; CORS uses configured origins rather than wildcard by default; tenant IDs are common in domain data and queries; sensitive secrets are referenced through environment variables in production Compose.

**Critical weaknesses:** production dev OTP defaults true; clinical raw values/evidence, model response previews, phone/email/recipient/patient data are logged; callback signatures are optional when secrets are blank. `/actuator/**` is public, including metrics/prometheus exposure configured by the app. HTTP Basic is enabled. CSRF is globally disabled while custom header/session authentication and credentialed CORS coexist; this may be safe by design but is not documented/proven. File upload lacks malware and magic-byte enforcement. No encryption-at-rest, retention, deletion/export, break-glass, or complete consent model is evidenced.

**Next:** execute the P0/P1 security backlog before rollout and commission a focused tenant/auth/file/provider threat review.

---

# 6. AI / Document AI Status

## 6.1 Provider configuration

| Use | Chain/default | Timeout/retry evidence |
|---|---|---|
| General AI | `gemini,groq,sarvam` via nested environment fallbacks | 60-second default; orchestration fallback and audit. |
| Clinical AI | `GEMINI,GROQ` | task-specific token limits; truncation normalization/retry logic. |
| AIVA V2 | `SARVAM,GEMINI,GROQ` | separate chain; V2 feature flag defaults false in local/UAT and is not wired in production Compose. |
| Document extraction | general provider orchestration plus Tesseract OCR | 4096 output token default; three attempts; one-minute backoff/job scheduler. |
| Voice STT | Sarvam, Faster-Whisper, mock in BFF; Whisper server in Python runtime | configured connect/read timeouts and fallback. |
| Voice TTS | ElevenLabs, Sarvam, Piper, mock | fallback and provider error classification. |

Configured default models are `gemini-2.5-flash`, `openai/gpt-oss-20b`, and `sarvam-105b-conversations`. These are configuration facts, not proof that production credentials, quotas, model availability, or data-processing agreements are current.

## 6.2 AIVA runtime state

- Legacy `PatientPortalCareAiService.java` is still 7,471 lines and remains the main Care assistant implementation.
- AIVA V2 exists as a separate controller, decision gateway, tools, transactional kernel, language adapters and session store. `AivaV2ConversationService` is 343 lines and the current `AivaV2SessionStore` is 169 lines before considering uncommitted diff size.
- V2 is a POC/baseline path and is disabled by default. Production Compose does not expose `AIVA_V2_ENABLED`, while local/UAT Compose default it to false.
- The previous audit’s core conclusion remains valid: legacy Care AI has multiple overlapping state/semantic authorities and high change risk. V2 improves separation of interpretation, language, tools, and transactions, but dual runtime plus uncommitted session-reset work increases release ambiguity.
- The current session store is process-local. **INFERRED:** restart and horizontal-scaling continuity require explicit durable/shared session semantics before V2 production cutover.

## 6.3 Safety and persistence

Positive evidence includes provider fallback, timeout/error classification, manual workflow independence, AI invocation logs, accepted/rejected/edited suggestion state, context hashes, stale status, doctor review, medication-safety acknowledgement, and document finding promotion rules.

Production blockers are sensitive logging, lack of a governed clinical evaluation corpus, and incomplete live-provider outage/quota certification. AI outputs are assistive and disclaimers exist, but a disclaimer does not replace safety validation.

---

# 7. RBAC / Security Findings

## 7.1 Authorization

- **VERIFIED:** 96 REST controllers, 83 containing `@PreAuthorize`, and 567 authorization annotations. Controllers without it are predominantly intentional public/auth/webhook endpoints or exception handlers; the public lab report verifier uses an opaque verification token.
- **VERIFIED:** Keycloak realm roles include platform/clinic/doctor/reception/billing/pharmacy/lab/auditor/Engage/service/viewer and legacy `ADMIN` roles.
- **VERIFIED:** `ADMIN` maps to tenant-admin permissions and remains actively referenced. It is compatibility code, not dead code.
- **VERIFIED:** backend Platform Tenant routes enforce `PLATFORM_ADMIN` even where two frontend routes lack a gate.
- **PARTIAL:** only a small number of tests explicitly advertise cross-tenant/other-tenant cases. This is insufficient for the breadth of APIs.

## 7.2 Authentication/session concerns

- Workforce: Keycloak JWT with issuer and timestamp validation; realm and client roles are normalized.
- Patient/provider: custom signed sessions and filters; OTP/access flows exist.
- Keycloak realm export has registration disabled, password reset enabled, email verification disabled, external SSL requirement, a public Admin client, and a service-account provisioner client.
- Production defaults for exposing dev OTP are unsafe and conflict with safer application defaults.
- Global CSRF disablement and enabled HTTP Basic should be intentionally justified or removed from production profile.

## 7.3 Audit/compliance

Audit/history exists across core domains, AI calls, documents/findings, billing, vaccination, inventory, provider lifecycle, alerts and events. Audit completeness is uneven because some state is in JSON snapshots/free-text and some newer persistence is in BFF. No complete privacy request, patient consent ledger, retention/deletion, legal hold, break-glass, or access-report workflow was found.

---

# 8. Database / Data Integrity Findings

## 8.1 Migration state

- **VERIFIED:** 168 migration files with versions V001–V168 and no duplicate version.
- **VERIFIED:** V114 is owned by `platform-events`; other migrations are primarily in `api-bff`. Classpath migration discovery should include it through dependencies, but packaged/runtime verification is still required.
- **VERIFIED:** V168 is `clinical_document_finding_review_metadata`.
- **VERIFIED:** repair migrations V101–V104 conditionally restore earlier clinical, identity, module and help structures. This indicates historical schema drift and increases the importance of clean-install and upgrade-path testing.
- **VERIFIED:** V057 deletes duplicate inventory rows before adding uniqueness; it is a destructive data-cleanup migration and should have backup/rollback evidence for any environment that had not already applied it.
- **VERIFIED:** V077 makes OTP `tenant_id` nullable for phone-only context; ambiguity handling exists in services, but this deliberately weakens direct tenant non-nullability.
- **UNVERIFIED:** actual production `flyway_schema_history`, checksums, row counts and constraints were not queried.

## 8.2 Static schema characteristics

The migration set contains approximately 194 `CREATE TABLE` statements/blocks, including conditional repair recreations, 135 foreign-key/reference occurrences, 130 unique constraints/indexes, and 20 JSONB declarations. A block scan found direct `tenant_id` in 124 create-table blocks; many remaining tables are global Commercial/Discover tables or children scoped through tenant-owned parents. This is not evidence that all indirect scoping is safe.

Risks:

- Child tables that rely only on parent joins are easier to query incorrectly than tables with direct tenant keys.
- JSON snapshots are appropriate for immutable audit/provider payloads, but inventory line items, extracted clinical structures and policy documents can become hard to constrain/query/migrate if treated as the sole business authority.
- Clinical Documents, Longitudinal, Laboratory, Pharmacy, Ops and Reliability persistence in BFF violates ownership rules.
- `scripts/db-verify.sh` only requires Flyway version 104 and checks a small table/column subset despite current V168; it can report success on a materially stale schema.

## 8.3 Required next checks

On a disposable production-like restore, run clean migration to V168, upgrade from the known production backup, compare schema objects/checksums/indexes/constraints, run orphan/tenant integrity queries, and start the application. Do not edit applied migrations.

---

# 9. Test / UAT Coverage

## 9.1 Test execution performed

| Command/area | Result |
|---|---|
| Repository architecture validator | Passed, but only validates a narrow Commercial Catalog boundary and misses verified BFF persistence violations. |
| Backend `mvn test` | Failed in `LeadServiceTest.createLeadPreservesFollowUpInstant`: hard-coded 2026-09-02 is now in the past. 167 CarePilot tests ran with one error in that module. |
| Earlier backend modules | Platform events 10, identity 31, clinic 35, patient 25, appointment 62, consultation 2, prescription 40, billing 39 (5 skipped), notification 38, AI 84, laboratory 1, vaccination 26; observed green except noted skips. |
| Groq clean module | 7/7 passed. An initial `NoSuchMethodError` disappeared after a clean dependency rebuild, showing stale incremental-build sensitivity. |
| Prescription focused reactor | 37/37 passed. A prior full-reactor attempt transiently failed class loading; focused clean dependency execution passed. |
| `web-admin` tests | 135/138 passed; doctor availability header, lead form fixed-date assertions, and prescription-intelligence source contract failed. |
| `web-care` tests | 22/22 passed. |
| `web-discover` tests | 30/30 passed. |
| `web-aiva` tests | No `test` script. |
| Frontend production builds | All four built. Admin main JS ~4.50 MB minified; Discover ~1.17 MB; Care ~723 KB; AIVA ~298 KB. All but AIVA emitted chunk-size warnings where applicable. |
| Backend package with tests skipped | **Passed** across all 45 modules needed by `api-bff`; compiled 768 BFF main and 293 BFF test sources and produced the Spring Boot JAR. This does not replace the red test run. |
| Python voice runtime tests | Could not import `pytest`; `pytest` is not in `requirements.txt`, so the repository does not provide an executable test environment. |

The 5 billing tests skipped when Testcontainers could not access Docker are the only explicitly observed skipped tests. No Playwright configuration, browser spec, or e2e directory was found. Frontend tests are primarily utility tests and source-text contracts, not rendered browser journeys.

## 9.2 UAT readiness matrix

| Module | Current coverage | Missing coverage | Readiness |
|---|---|---|---|
| Tenant/Admin | Service/controller/source contracts | Cross-tenant, realm provisioning, suspend/reactivate, role negatives | **Medium** |
| Reception/Appointments | Strong service/unit lifecycle tests | Browser journey, concurrency, payment/queue edge cases | **Medium-high** |
| Doctor Workspace | Many BFF/UI contracts; thin domain tests | Full visit, reopen/correct, clinical role handoff | **Medium** |
| Doctor AI/Safety | Extensive unit/parser/provider/fallback tests | Live-provider, clinical benchmark, quota/outage, adversarial | **Medium** |
| Documents/Longitudinal | Good service/controller/repository tests | Real file corpus, malware, multi-report conflict, patient isolation | **Medium** |
| Laboratory | Many BFF/UI contracts; one domain test file | Full specimen/result/amend/publication role journey | **Low-medium** |
| Pharmacy | UI/BFF tests; no substantive inventory domain tests | Concurrent stock, procurement/returns/reconcile browser flow | **Low-medium** |
| Vaccination | Good service/RBAC/CSV tests | Browser roles, concurrent duplicate and AEFI/report flow | **Medium-high** |
| Billing | Service tests; PostgreSQL tests skipped | DB-backed lifecycle, settlement/reconcile/tax/refund authorization | **Medium-low** |
| Engage | Rich domain tests, but one is date-expired | Real provider sandbox, consent/rate, webhook security/retry | **Medium-low** |
| Discover | 119 domain tests and 30 frontend contracts | Browser/mobile/geolocation/SEO/accessibility | **Medium-high** |
| Care | 22 frontend contracts and backend tests | Multi-clinic privacy, browser auth/session/booking/documents | **Medium** |
| Provider | Strong domain/service tests | Real approval/login/publication/revocation UAT | **Medium** |
| Platform Ops | Focused service/security tests | Real host/container/TLS/backup/alert integration | **Low-medium** |
| Voice | Provider/unit/WebSocket tests | Python test environment, real audio, load/latency/failover | **Low** |
| Security | Many controller security tests | Complete role×route×tenant negative matrix, file/webhook threat tests | **Low-medium** |

## 9.3 Highest-value missing tests

1. Playwright two-tenant, multi-role clinic journey from registration through billing and report visibility.
2. Patient/provider OTP/session negative tests against production profile defaults.
3. Cross-tenant API matrix for all IDs in path/query/body and presigned object access.
4. Lab specimen→partial result→verification→publication→patient journey.
5. Pharmacy procure→receive→dispense→return→physical-count journey with concurrent stock writes.
6. Billing partial payment/refund/outstanding invariants against PostgreSQL.
7. AI outage/quota/truncation/fallback/manual-workflow isolation.
8. Multi-report Document AI review/promotion/rejection and patient isolation.
9. Engage invalid/unsigned/duplicate webhook, consent, rate and provider retry tests.
10. Backup restore drill for clinic DB, Keycloak DB and MinIO followed by application smoke/UAT.

---

# 10. Infrastructure / Deployment State

Production Compose includes PostgreSQL 15, Redis 7, MinIO, Keycloak 24, API, all four frontends, Whisper, Faster-Whisper, Piper and the Python realtime voice gateway. Containers generally use `restart: unless-stopped`; PostgreSQL, Keycloak, API/frontends and voice services have health checks.

Hardening gaps:

- Redis and MinIO lack Compose health checks, and API dependency ordering uses service-started semantics for them.
- No CPU/memory/PID limits or reservations were found.
- `minio/minio:latest` and `whisper.cpp:main` are floating tags.
- Voice models/binaries download from the internet during image startup/build without pinned checksums.
- TLS termination and certificate renewal are outside the repository; SSL expiry is not monitored.
- Production ports are mostly bound to loopback, which is a positive reverse-proxy boundary, but the proxy/IaC configuration is not part of this repository.
- No HA/orchestrator/replication topology, autoscaling, rolling migration strategy, or multi-host DR is evidenced.
- Default application config contains development DB/Keycloak/MinIO credentials and local session defaults. Production Compose expects overrides, but fail-safe validation is incomplete.
- No tracked production environment example/template was found; only a UAT example is present. This makes required production settings less auditable.

Failure behavior:

- PostgreSQL/Keycloak outages prevent core operation, as expected; health/readiness coverage exists but restore/failover does not.
- Redis is health-probed but exact degradation of all consumers is not comprehensively tested.
- MinIO failures surface in document operations; manual clinic workflows can continue only where documents are not required.
- AI failures are generally isolated behind fallback/manual workflows.
- Scheduler state is partly in memory, reducing continuity/observability across restarts.

---

# 11. Backup / Recovery State

## Verified backup implementation

`local/scripts/backup-production.sh`:

- creates PostgreSQL custom-format dumps for `clinic_management` and Keycloak;
- validates both with `pg_restore -l`;
- mirrors every MinIO bucket using `minio/mc:latest`;
- uses POSIX shell parameter expansion for bucket parsing, matching the recent operational fix;
- generates SHA-256 checksums for the two DB dumps;
- keeps 14 days locally;
- is scheduled daily at 02:00 through systemd assets.

The user-provided operational baseline states DB, Keycloak DB and MinIO backup completed successfully after the compatibility fix. That is accepted as external operational evidence, not reproduced here.

## Gaps

- No encrypted or off-host/immutable copy is implemented in the script.
- MinIO content is not checksummed as a complete backup set.
- No lock prevents overlapping backup runs.
- No structured status/age/size/duration output feeds Platform Ops; dashboard state is `UNKNOWN`.
- The systemd service calls `/opt/jeevanam/bin/backup-jeevanam-prod.sh`, while the repository implementation lives under `local/scripts`; deployment-copy consistency is unverified.
- Repository restore scripts restore only the clinic PostgreSQL database. No complete production procedure restores Keycloak plus MinIO and validates cross-store consistency.
- No periodic restore drill, RPO/RTO measurement, point-in-time recovery, remote retention or disaster-recovery runbook execution evidence exists.

Status: **PARTIAL**. Backup creation has operational evidence; recoverability of the whole platform does not.

---

# 12. Platform Ops / Observability State

Phase 1A is a meaningful operational foundation, not yet a production-readiness dashboard. It has internal health matrix, release metadata, AI activity, DB pool/schema state, provider/queue metrics, scheduler views, alerts and DLQ replay. It lacks the infrastructure and operational truth required to answer “is production healthy and recoverable?” in one place.

Priority additions:

1. backup last-success/age/size/checksum/restore-drill state;
2. host and container CPU/memory/disk/restart/OOM/health;
3. TLS certificate expiry and endpoint probe;
4. login success/failure/session/activity aggregates without exposing PHI;
5. all external integration readiness, quota/rate/last error and circuit state;
6. persistent scheduler heartbeat and stuck-job age across restarts;
7. incident/paging acknowledgement and escalation integration;
8. deployment/version drift across containers;
9. data-store capacity, connection saturation and disk growth;
10. safe diagnostics policy so probes cannot create cost or mutate state without audit/limits.

---

# 13. Known Technical Debt

- `api-bff` violates its inbound-adapter boundary with entities, repositories and business persistence.
- Architecture validation is narrow and gives a false sense of constitution compliance.
- Legacy Care AI is a 7,471-line service with overlapping state, resolution, workflow and response responsibilities.
- AIVA V2 is a parallel runtime, disabled by default, with in-process session state and active uncommitted work.
- Commercial and legacy entitlement authorities coexist.
- Lab core and lifecycle persistence are split across BFF/domain modules.
- Pharmacy has duplicate procure/reconcile pages and explicit test routes.
- Frontend source-contract tests are brittle; multiple tests now fail because copy/layout changed or fixed dates expired.
- Production/UAT/local configuration chains differ; some flags are not wired consistently.
- Large frontend bundles, especially Admin, lack code splitting.
- Runtime/scheduler telemetry contains in-memory state.
- Static helper scripts have stale scope (`db-verify` at V104, `build-all` omits two apps, smoke omits critical services).
- JSON/free-text duplicates some structured clinical and inventory authority.
- Generated/bundled scratch artifacts are tracked under hidden `web-admin` directories.

---

# 14. Legacy / Duplicate / Dead Code

| Item | Classification | Evidence / action |
|---|---|---|
| `platform-idempotency` | **SCAFFOLDED** | Empty module; either implement a shared contract or remove from reactor after dependency review. |
| `platform-jobs` | **SCAFFOLDED** | Empty module; same action. |
| Legacy Care AI + AIVA V2 | **PARTIAL** dual implementation | Legacy remains runtime authority; V2 is separate and disabled. Define cutover/deprecation plan before more duplication. |
| Java realtime gateway + Python runtime | **UNKNOWN - INSUFFICIENT EVIDENCE** | Java is built; production Compose deploys Python. Choose the owning runtime and retire or document the other. |
| Commercial + legacy entitlements | **PARTIAL** dual authority | UI explicitly states legacy remains authoritative. Complete cutover and compatibility plan. |
| BFF lab persistence + laboratory domain | **PARTIAL** duplication/split ownership | Consolidate under Laboratory bounded context through forward migrations. |
| BFF pharmacy persistence + inventory domain | **PARTIAL** split ownership | Inventory domain should own business persistence. |
| Pharmacy duplicate routes/pages | **LEGACY / DEPRECATED** candidates | `/procure` vs `/procurement`, `/reconcile` vs `/reconciliation`, old inventory/medicine routes and `*-test` routes. Remove only with route compatibility plan. |
| `ADMIN` role | **LEGACY / DEPRECATED** compatibility role | Still wired and maps to tenant-admin permissions; cannot be removed without identity migration. |
| Hidden web-admin bundle directories | **LEGACY / DEPRECATED** generated artifacts | Tracked `.web-admin-landing-*` and `.commercial-entitlements-*` artifacts should not be source authority. |

No source-only “abandoned feature branches” can be proven from repository layout alone. The parallel runtimes and duplicate pages above are concrete branch-like implementations; anything else would be speculation.

---

# 15. Production Risks

## P0 production blockers

1. **P0-01 — Development OTP fail-open:** production Compose defaults both patient and provider `EXPOSE_DEV_OTP` to true.
2. **P0-02 — PHI/PII/model payload logging:** clinical values/evidence, raw/normalized AI responses, phone/email/recipient/patient information can be logged at INFO/WARN.
3. **P0-03 — No green immutable release candidate:** HEAD is seven commits beyond production, working tree is dirty, backend suite is red, Admin tests are red, and no browser E2E exists.
4. **P0-04 — Whole-platform recovery unproven:** backup creation exists, but no complete Keycloak+MinIO+clinic restore automation/drill, remote immutable copy, or dashboard status exists.
5. **P0-05 — Webhook authentication can fail open:** CarePilot callbacks are public and signature validation is conditional on secrets being nonblank; production Compose does not visibly require/wire those secrets.

## P1 high-priority risks

1. **P1-01:** enforce architecture boundaries and move BFF persistence to owning domains.
2. **P1-02:** add Playwright role/tenant/browser regression suite.
3. **P1-03:** complete Platform Ops backup/container/host/disk/SSL/user/integration telemetry.
4. **P1-04:** restrict Actuator exposure; review HTTP Basic, CSRF/session/CORS and reverse-proxy policy.
5. **P1-05:** add file signature validation, malware scanning, encryption/retention/access controls.
6. **P1-06:** pin container/model artifacts and add resource limits/readiness for Redis/MinIO.
7. **P1-07:** complete Commercial entitlement cutover and remove dual authority.
8. **P1-08:** reconcile Laboratory persistence ownership and add full lifecycle tests.
9. **P1-09:** consolidate Pharmacy routes/ownership and add inventory concurrency tests.
10. **P1-10:** mature finance settlement/reconciliation/tax/credit controls before broader rollout.
11. **P1-11:** choose one voice runtime, make tests executable, and run latency/load/failover UAT.
12. **P1-12:** resolve AIVA legacy/V2 authority, durable session model, and production flag/config policy.
13. **P1-13:** enforce runtime communication consent/rate limits and provider callback replay protection.
14. **P1-14:** repair build/smoke/schema verification scripts and reduce frontend bundle size.

---

# 16. Product Gaps

## P0 gaps

- Safe production authentication defaults and fail-closed provider callbacks.
- PHI-safe logging/observability.
- Reproducible green release/UAT gate.
- Complete, tested recovery for all system-of-record stores.

## P1 gaps

- Production-readiness dashboard and actionable alerting.
- Comprehensive tenant/role/browser security certification.
- File/PHI lifecycle controls and patient access audit.
- Finance reconciliation/settlement/tax/accounting depth.
- Real provider messaging consent, rate enforcement and delivery certification.
- Operationally supported AI/model governance and voice runtime.

## P2 product-maturity gaps

- Insurance/TPA claims, preauthorization, adjudication and payer settlement.
- Advanced analytics and standardized finance/clinical operational reports.
- Patient-level consent ledger, granular communication preferences, privacy requests and retention policy.
- External interoperability such as ABDM/FHIR/HL7/LIS/insurance integrations.
- Multi-location stock/accounting consolidation and stronger procurement controls.
- Native mobile/push communication and richer patient self-service.
- True server-rendered/metadata-driven public SEO.
- Formal incident, support and privileged break-glass workflows.

## P3 future roadmap

- IPD: admissions, wards/beds, nursing, inpatient orders/MAR, discharge and inpatient billing.
- Claims clearinghouse/payer network integrations.
- Multi-region HA/DR and horizontal voice/AI/session architecture.
- Advanced population health, cohorts, predictive risk and outcomes analytics.
- Broader device/analyzer/e-prescribing ecosystem integrations.

---

# 17. P0 / P1 / P2 / P3 Backlog

## P0 — before any current-HEAD production release (5)

| ID | Deliverable | Acceptance evidence |
|---|---|---|
| P0-01 | Force dev OTP off in production and fail startup if enabled. | Production-profile config test plus black-box auth test. |
| P0-02 | Redact/remove PHI/PII/raw model payload logs. | Automated log-capture tests and security review. |
| P0-03 | Freeze a clean commit and make backend/Admin/Care/Discover/AIVA test/build gate green. | CI artifact tied to commit/tag; no dirty files. |
| P0-04 | Restore clinic DB, Keycloak DB and MinIO into disposable environment and run smoke/UAT. | Timed restore report, checksums, RPO/RTO, application validation. |
| P0-05 | Require webhook secrets/signatures and replay protection in production. | Invalid/missing/duplicate signature tests and startup validation. |

## P1 — before broader rollout (14)

Use the numbered P1 risks from Section 15 as the rollout backlog. Each item needs an owner, approved spec, dependency/placement review, test evidence and rollback plan.

## P2 — product maturity

- finance/accounting depth;
- insurance/TPA foundation;
- consent/privacy/retention workflows;
- interoperability and integration management;
- analytics/reporting expansion;
- inventory/procurement consolidation;
- SEO/mobile/accessibility hardening;
- support/incident tooling.

## P3 — future roadmap

- IPD and inpatient clinical operations;
- payer clearinghouse integration;
- regional HA/DR;
- advanced population health/AI;
- broader device and national-health-network integration.

---

# 18. Recommended Next 30 Days

1. Freeze current feature work into a clean, named release-candidate branch; inventory and resolve the six local AIVA/Care changes.
2. Close P0-01, P0-02 and P0-05 with production-profile tests.
3. Repair time-dependent backend/Admin tests using injected/fixed clocks; resolve the doctor/prescription UI contract failures.
4. Run a clean `scripts/build-all.sh` equivalent that includes Discover and AIVA; publish all artifacts from one commit.
5. Add a first Playwright pack: platform admin tenant creation, receptionist appointment/check-in, doctor consultation/prescription, lab result, pharmacy dispense, billing payment, patient report view.
6. Build the two-tenant authorization matrix and automate the highest-risk identifiers/object URLs.
7. Perform full restore drill and add structured backup telemetry to Platform Ops.
8. Disable public Actuator surfaces except deliberate health/readiness; review Basic/CSRF/CORS/session design.
9. Decide and document ownership/cutover for AIVA V2, Commercial entitlements, Lab persistence and voice runtime.
10. Pin production images/models and set resource limits/readiness for Redis/MinIO/voice.

---

# 19. Recommended Next 90 Days

1. Complete P1 architecture extraction in forward-only batches, beginning with Laboratory and Clinical Documents/Longitudinal persistence.
2. Expand Playwright to all key roles, negative authorization, mobile Care/Discover and provider onboarding.
3. Implement file malware/signature validation, PHI retention/encryption/access audit, and patient privacy/consent foundations.
4. Finish Platform Ops Phase 1B with host/container/storage/TLS/user/integration/incident signals and alert routing.
5. Complete Commercial runtime cutover with compatibility, comparison, rollback and billing-contract tests.
6. Consolidate Pharmacy UI/runtime and add PostgreSQL inventory concurrency/invariant testing.
7. Deliver finance settlement/reconciliation/tax/credit-note requirements and reporting; scope insurance separately.
8. Establish AI model governance: approved models, evaluation corpus, release thresholds, cost/quota controls, prompt/version audit and rollback.
9. Select the voice runtime, pin its dependencies/models, and certify real-device latency/load/fallback/privacy.
10. Establish quarterly restore/DR drills and measurable SLOs for API, DB, identity, storage, provider delivery and critical schedulers.

---

# 20. Evidence Appendix

## 20.1 Core documents read

- `AGENTS.md`
- `backend/api/api-bff/AGENTS.md`
- `backend/domains/AGENTS.md`
- `web-admin/AGENTS.md`
- `docs/architecture/ARCHITECTURE_CONSTITUTION.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/BACKEND_LAYERING.md`
- `docs/architecture/FRONTEND_ARCHITECTURE.md`
- `docs/specs/README.md` and applicable approved specs by module
- prior `analysis.md` AIVA audit

## 20.2 Key evidence paths

| Topic | Paths |
|---|---|
| Backend reactor | `backend/pom.xml`, root `pom.xml` |
| Security | `backend/api/api-bff/.../config/SecurityConfig.java`, `backend/platform/platform-security-spi/...` |
| Tenant admin | `backend/api/api-bff/.../platform/PlatformTenantController.java`, identity/clinic domains |
| Reception | appointment domain; Admin appointment/dayboard/queue pages |
| Doctor/AI | consultation/prescription/AI domains; `api/ai`, `api/consultation`, `api/medicationsafety` |
| Documents/longitudinal | `api/clinicaldocument`, `api/clinicalmemory`, storage-minio, OCR providers |
| Lab | `api/lab`, `laboratory-domain`, `web-admin/src/pages/lab` |
| Pharmacy | inventory domain, `api/pharmacy`, `web-admin/src/pages/pharmacy` |
| Vaccination | vaccination domain, `api/vaccination`, Admin vaccination pages/tests |
| Billing | billing domain, `api/billing`, Admin billing/finance pages |
| Engage | carepilot domain, `api/carepilot`, Admin CarePilot products |
| Discover/provider | discover domain, `api/discover`, `web-discover`, provider/platform pages |
| Care | `api/patientportal`, `web-care` |
| Platform Ops | `api/ops`, `web-admin/src/pages/admin/PlatformOpsPage.tsx` |
| Voice | `api/voice`, `api/patientportal/voice`, both `backend/realtime` runtimes, Compose voice services |
| Migrations | all `*/src/main/resources/db/migration/V*.sql` |
| Deployment | `local/docker-compose*.yml`, Dockerfiles, `scripts/*.sh`, `local/systemd/*` |
| Backup | `local/scripts/backup-production.sh`, restore scripts and recovery runbooks |

## 20.3 Important measured counts

- Backend reactor: 48 projects in the full test order.
- BFF: 768 Java main sources compiled; 293 test sources compiled in package verification.
- Frontends: Admin 254 source / 138 tests; Care 39 / 22; Discover 58 / 30; AIVA 8 / 0.
- REST controllers: 96.
- `@PreAuthorize` annotations: 567.
- Flyway migrations: 168, V001–V168, no duplicates.
- BFF architecture violations: 28 entities, 27 repositories, 13 persistence-style directories.
- Post-production delta: 7 commits, 275 files, approximately +37.4k/-1.3k lines.

## 20.4 Test interpretation caveats

- Tests were run against the local repository and uncommitted working tree, not the deployed production release.
- No paid provider calls were made.
- No Docker-dependent integration test or production database was forced when unavailable.
- A passing source-contract test means required source text is present, not that the user journey works.
- A package build with `-DskipTests` proves compilation/package wiring, not Spring context/database/provider startup.
- Runtime deployment, applied Flyway state, backups, alerts and provider credentials remain unverified unless explicitly identified as user-supplied operational evidence.

## 20.5 Final consistency review

The final pass rechecked:

- all completion claims against corresponding UI routes, backend controllers/services, persistence and tests;
- frontend platform visibility against backend `PLATFORM_ADMIN` enforcement;
- controller authorization counts and intentional public endpoints;
- Flyway order/duplicates and current highest version;
- the production OTP defaults, public Actuator rule and explicit backup `UNKNOWN` implementation;
- the absence of Playwright/e2e assets;
- final Git HEAD, release delta and working-tree changes.

No module is classified `COMPLETE` without qualification. Areas with strong implementation but missing operational/browser proof are classified `COMPLETE - NEEDS HARDENING` or `IMPLEMENTED - NEEDS UAT`; materially incomplete business/ops surfaces are `PARTIAL`.
