---
spec_id: JCP-LAB-TEST-LEVEL-LIFECYCLE-PARTIAL-PUBLICATION
title: Laboratory Test-Level Lifecycle and Partial Publication
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

Add independent ordered-test processing while preserving the validated order-level laboratory workflow as bulk compatibility behavior.

# Scope

- Ordered-test lifecycle and derived aggregate order status.
- Many-to-many specimen-to-ordered-test associations.
- Append-only result revisions for correction after send-back.
- Per-test approval, result send-back, and recollection request.
- Selected or all-eligible partial publication with immutable publication artifacts and included result revisions.
- Conditional mixed-state UI inside existing collection, verification, and publication surfaces.

# Deferred

- Outsourced/reference-lab processing and `AWAITING_EXTERNAL_RESULT`.
- Corrected, amended, superseded, interim, and final report-version semantics.
- Report merging/grouping and post-publication correction notifications.

# State Model

Ordered tests use: `ORDERED`, `PAYMENT_PENDING`, `READY_FOR_COLLECTION`, `SAMPLE_COLLECTED`, `SAMPLE_RECEIVED`, `RESULT_DRAFT`, `RESULT_ENTERED`, `RESULT_SENT_BACK`, `RECOLLECTION_REQUIRED`, `VERIFIED`, and `PUBLISHED`.

The aggregate order retains existing statuses when children are uniform. Mixed children derive `IN_PROGRESS`, `PARTIALLY_READY`, or `PARTIALLY_PUBLISHED`; no child is advanced merely to match another child.

# Compatibility

- Existing collect, receive, result-entry, verify, publish, view, print, and download contracts remain valid.
- Requests without ordered-test selections operate over all eligible children.
- One order-level collection may associate one specimen to all eligible tests requiring the same specimen.
- Existing results and published reports remain authoritative and viewable.
- Granular request fields and response projections are additive and optional.

# Persistence And Backfill

- `laboratory-domain` owns all new entities and repositories.
- Flyway execution remains in `api-bff` resources; migration ownership is laboratory-workflow.
- Existing item IDs, order IDs, sample IDs, result IDs, accessions, payments, reports, and audits are unchanged.
- Backfill creates one lifecycle row per existing `lab_order_items` row and derives only the strongest state supported by persisted order/result/sample/publication evidence.
- Existing item-specific sample links are retained; order-wide samples are linked to every compatible ordered test.
- Existing result rows become revision 1 without modifying the source rows.
- Existing published reports receive an artifact snapshot referencing all existing ordered tests/results without replacing the stored report.

# API Behavior

- Verify accepts optional ordered-test IDs and recollection as a decision; omitted IDs mean all eligible tests.
- Publish accepts optional ordered-test IDs; omitted IDs mean all eligible verified tests.
- Order detail returns child lifecycle state, eligibility, specimen links, latest revision, verification, and publication inclusion.
- Existing authorization annotations and tenant context remain authoritative.

# Validation Order

1. Run the existing two-test happy path through payment, shared collection, receive, draft/reopen, results, approve-all, publish-all, and report access.
2. Only after it passes, run mixed approval/send-back, independent publish, correction/re-entry, recollection, shared rejection, unaffected-test continuation, tenant isolation, and RBAC tests.

# Planned Files

- `backend/domains/laboratory-domain/pom.xml`
- `backend/domains/laboratory-domain/src/main/java/com/deepthoughtnet/clinic/laboratory/**`
- `backend/api/api-bff/src/main/resources/db/migration/V158__laboratory_test_level_lifecycle.sql`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/**`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/lab/**`
- `web-admin/src/api/clinicApi.ts`
- `web-admin/src/pages/lab/LabPage.tsx`
- `web-admin/test/lab-test-level-lifecycle.test.mjs`

# Rollback

Application rollback ignores additive child tables and optional fields. The migration is not reversed destructively; existing order-level records remain intact and authoritative for the legacy path.
