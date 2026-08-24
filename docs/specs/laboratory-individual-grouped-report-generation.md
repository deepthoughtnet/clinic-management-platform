---
spec_id: JCP-LABORATORY-INDIVIDUAL-GROUPED-REPORT-GENERATION
title: Laboratory Individual, Grouped, and Final Report Generation
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: laboratory-workflow
domain_module: laboratory-domain
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Add additive report artifact generation modes for laboratory orders without changing the validated ordered-test lifecycle, publication audit trail, or tenant isolation behavior.

# Scope

- Report artifact mode support for `INDIVIDUAL`, `GROUPED`, and `CONSOLIDATED`.
- Immutable report versions with current/historical/superseded reporting status.
- Report history and current-report selection for patient-visible workflows.
- PDF layout stabilization for production printing and digital viewing.
- Existing test publication lifecycle remains authoritative and separate from report composition.

# Boundary

- In scope: report artifact persistence, versioning, history, verification identity, PDF composition, and lab UI report-mode selection.
- In scope: additive APIs for report generation and report history retrieval.
- Out of scope: result-entry semantics, verification semantics, recollection semantics, specimen association rules, and unrelated laboratory pages.

# Ownership

- `laboratory-domain` owns report artifact persistence and report-history business rules.
- `api-bff` owns HTTP orchestration, request/response mapping, PDF rendering, and public verification presentation.
- `web-admin` owns the report-generation chooser, report history/current-report UI, and patient-visible report presentation.

# Behavior

- Individual reports may contain exactly one selected eligible test.
- Grouped reports may contain multiple selected eligible tests from the same order.
- Consolidated reports may contain all final eligible tests.
- Generating a report artifact must not duplicate child-test publication lifecycle events for tests already published.
- Each report artifact stores version, mode, type, status, included ordered-test IDs, verification identity, and delivery metadata.
- The latest generated artifact is the current report; earlier artifacts remain viewable in history.
- Final reports are business-readable and must not expose raw backend enum names to patients.
- The PDF must render in A4 portrait with compact, printable sections and no trailing blank page.

# Compatibility

- Existing publish-all happy path remains valid.
- Existing order-level lifecycle statuses remain derived convenience states.
- Existing verification token behavior remains tenant-safe and immutable per artifact.
- Existing audit trail and RBAC remain authoritative.
- Report artifacts are append-only; prior artifacts are never overwritten.

# Validation

- Verify individual, grouped, and final report generation each persist a distinct artifact version.
- Verify current-report selection resolves to the latest artifact while preserving history.
- Verify PDF layout fits a single page when content allows and paginates cleanly for long content.
- Verify verification URLs are absolute, public, and tenant-safe for each artifact.
- Verify grouped report generation does not duplicate publication lifecycle events for already published tests.

# Planned Files

- `backend/api/api-bff/src/main/resources/db/migration/V159__laboratory_report_artifact_versioning.sql`
- `backend/domains/laboratory-domain/src/main/java/com/deepthoughtnet/clinic/laboratory/**`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/**`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/lab/**`
- `web-admin/src/api/clinicApi.ts`
- `web-admin/src/pages/lab/LabPage.tsx`
- `web-admin/test/lab-report-generation-ux.test.mjs`

