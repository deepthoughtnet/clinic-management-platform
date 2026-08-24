---
spec_id: JCP-LABORATORY-REPORT-PUBLICATION-VERIFICATION-PRESENTATION
title: Laboratory Report Publication Verification and Presentation
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: laboratory-workflow
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Stabilize the published laboratory report experience without changing the laboratory lifecycle semantics.

# Boundary

- In scope: publish dialog delivery channel fidelity, published report CTA, report timestamp presentation, report verification identity, QR generation, report header/metadata presentation, and canonical report rendering consistency.
- In scope: tenant-safe public verification data exposure for published reports.
- In scope: forward-only database changes if required to support immutable report verification identity.
- Out of scope: laboratory order lifecycle redesign, sample/result semantics, result entry editing, approval workflow semantics, and unrelated laboratory page redesign.

# Ownership

- `api-bff` owns published report projection, verification identity generation, PDF rendering, and public verification endpoint orchestration.
- `web-admin` owns publish dialog state, post-publication success messaging, and laboratory report presentation in the UI.
- Persistence remains in the laboratory bounded context and is only extended additively if required.

# Behavior

- Publishing must queue only the delivery channels explicitly selected by the approver.
- Unselected delivery channels remain skipped or not requested in both persistence and success feedback.
- Post-publication actions must return the user to a report-appropriate laboratory destination, not to sample collection.
- Report timestamps render in the tenant/clinic timezone for display only.
- A published report exposes an immutable, tenant-safe verification identity and an externally usable absolute verification URL.
- The PDF/report includes a scannable QR code that resolves to that verification URL.
- Report header and metadata use business-facing labels and hide internal enum names.
- Review/approval metadata is sourced from actual workflow audit/state and must not fabricate actors or timestamps.
- All report surfaces must render from the same canonical report model.
- Published PDFs use an A4 portrait, page-aware layout with stable margins, footer placement, and repeated results headers after page breaks.
- Verification is rendered as a dedicated non-splitting block with a wrapped display URL/reference and a reserved, padded QR column; the QR payload remains the complete secure URL.
- Long clinic identity, patient metadata, result sets, and comments must wrap or paginate without overlap, clipping, or orphaned section headers.

# Compatibility

- Persisted timestamps remain unchanged.
- Existing laboratory lifecycle transitions remain unchanged.
- Existing audit trail and RBAC rules remain authoritative.
- Any new persisted verification identity must be added additively and backfilled safely.

# Validation

- Verify publish success reflects exactly the selected channels.
- Verify unselected channels remain skipped/not requested.
- Verify post-publish CTA returns to a lab-appropriate destination.
- Verify timestamps display in tenant local time while persistence remains UTC.
- Verify QR payload is absolute, public, and tenant-safe.
- Verify report verification rejects invalid, unknown, or tenant-mismatched identifiers.
- Verify report header labels are business-readable and no empty logo placeholder is rendered.
- Verify all report surfaces consume the same canonical model.
- Generate a normal single-page fixture and a long-content, multi-page fixture; verify page bounds, repeated result headings, intact verification block, and stable footer/page numbering.

# File ownership map

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/LabController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/db/LabOrderEntity.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/dto/LabOrderResponse.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/service/LabService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalService.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/lab/service/LabServiceValidationTest.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/lab/LabControllerRouteTest.java`
- `web-admin/src/api/clinicApi.ts`
- `web-admin/src/pages/lab/LabPage.tsx`
- `web-admin/test/lab-report-delivery.test.mjs`
- `web-admin/test/lab-report-publication-ux.test.mjs`
