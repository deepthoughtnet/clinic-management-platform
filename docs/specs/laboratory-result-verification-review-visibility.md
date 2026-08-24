---
spec_id: JCP-LABORATORY-RESULT-VERIFICATION-REVIEW-VISIBILITY
title: Laboratory Result Verification Review Visibility
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: laboratory-workflow
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Ensure the laboratory approver sees the complete entered result set, technician comment, and available entry metadata before taking any verification action.

# Boundary

- In scope: laboratory verification dialog content, read-only result presentation, abnormal/critical highlighting, and result-entry metadata exposure for review.
- In scope: reuse of authoritative lab order/result projections already used by entry, queues, and report generation.
- Out of scope: laboratory order state machine changes, result-entry editing changes, approval action changes, schema migrations, and unrelated laboratory page redesign.

# Ownership

- `api-bff` owns the laboratory HTTP adapter and review projection assembly.
- `web-admin` owns the laboratory review dialog rendering and visual emphasis.
- Persistence remains in the laboratory bounded context and is not changed for this fix.

# Behavior

- Opening Lab Verification shows the full ordered test/result list before verification controls.
- Result rows are read-only for the approver.
- Technician-entered comments are visible before approval or send-back actions.
- Available result-entry metadata is shown without fabricating missing actor data.
- Normal, abnormal, and critical result states remain driven by backend-provided result flags.
- Critical results are visually prominent and surfaced before the approval controls.

# Compatibility

- Existing approve and send-back/reject flows remain unchanged.
- Existing tenant isolation, audit trail, and RBAC enforcement remain authoritative.
- No destructive migration or storage schema change is required for this visibility fix.

# Validation

- Verify the review dialog shows every result row for multi-parameter tests.
- Verify units, reference ranges, critical ranges, and result status are visible.
- Verify technician comments render before the controls.
- Verify result rows cannot be edited from the approver dialog.
- Verify approve and send-back actions still submit successfully.
- Verify existing result entry and report publishing behavior remain intact.

# File ownership map

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/LabController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/service/LabService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/service/model/LabOrderRecord.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/dto/LabOrderResponse.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/lab/service/LabServiceValidationTest.java`
- `web-admin/src/api/clinicApi.ts`
- `web-admin/src/pages/lab/LabPage.tsx`
- `web-admin/test/lab-result-verification-review-ux.test.mjs`
