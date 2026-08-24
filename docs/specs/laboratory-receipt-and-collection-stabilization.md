---
spec_id: JCP-LABORATORY-RECEIPT-AND-COLLECTION-STABILIZATION
title: Laboratory Receipt Identity and Sample Collection Stabilization
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: laboratory-workflow
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Stabilize the laboratory workflow after UAT by correcting receipt identity hydration, aligning sample-collection visibility with the effective lab permission model, and fixing the sample-collection modal lifecycle after success.

# Boundary

- In scope: laboratory payment receipt projection, receipt print/preview rendering, sample-collection action visibility, and successful sample-collection dialog lifecycle.
- In scope: authoritative clinic profile hydration, patient identity hydration, collector display-name projection, and single-submit collection behavior.
- Out of scope: lab pricing, order state machine, accession format, result entry persistence, approval flow, report publishing, and tenant isolation semantics.
- Out of scope: broader RBAC redesign or destructive schema changes.

# Ownership

- `api-bff` owns laboratory HTTP adapters and the lab workflow orchestration surface.
- `billing-domain` remains the source of truth for business-readable staff identity resolution used by receipts.
- `web-admin` owns the laboratory workspace rendering, action gating, and printable receipt UI.

# Behavior

- Laboratory receipt preview and print reuse the same receipt projection and template.
- Receipt header shows authoritative clinic/lab identity, including registration number when present.
- Receipt patient mobile comes from the linked patient record.
- Receipt collector/received-by uses a business-readable display name and never raw UUIDs as the rendered business value when a user record exists.
- Lab Front Desk does not see sample-collection entry points unless the effective permission model grants `lab.order.collect_sample`.
- Successful sample collection closes the modal, clears local form state, prevents duplicate submission, refreshes lab queues, and surfaces one success notification.

# Compatibility

- No existing receipt numbering, payment amounts, item lines, or workflow transitions are changed.
- Existing unauthorized backend access remains enforced.
- Existing successful lab order creation, billing, sample receive, result entry, approval, and publish paths remain available.

# Validation

- Verify laboratory receipt preview and print use the same corrected receipt projection.
- Verify clinic identity and patient mobile render from persisted data.
- Verify collector display-name fallback never renders raw UUIDs when a user record exists.
- Verify Lab Front Desk cannot see collect-sample actions.
- Verify Lab Assistant retains collect-sample visibility.
- Verify sample collection fires one mutation, disables submission while pending, closes the modal on success, and preserves retry on genuine failure.

# File ownership map

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/LabController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/service/LabService.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/lab/service/LabServiceValidationTest.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/lab/LabControllerRouteTest.java`
- `web-admin/src/pages/lab/LabPage.tsx`
- `web-admin/src/components/finance/PrintableBillingDocuments.tsx`
- `web-admin/test/lab-page-rbac.test.mjs`
- `web-admin/test/lab-payment-receipt.test.mjs`
- `web-admin/test/lab-sample-collection-ux.test.mjs`
