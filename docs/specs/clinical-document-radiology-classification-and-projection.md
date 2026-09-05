---
spec_id: JCP-CLINICAL-DOCUMENT-RADIOLOGY-CLASSIFICATION-PROJECTION
title: Clinical Document Radiology Classification and Projection
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: clinicaldocument
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Ensure radiology/imaging documents are canonically classified and projected as radiology instead of falling back to external lab semantics, while preserving laboratory document behavior.

# Boundary

- In scope: clinical document type normalization, canonical document-type repair, patient timeline projection, patient documents projection, radiology list visibility, and frontend label/filter rendering driven by canonical document type.
- In scope: safe additive repair of already-persisted documents whose title/filename/description clearly indicate radiology but whose stored type is still lab-like.
- Out of scope: AI provider behavior, clinical reasoning prompts, prescription logic, finding-review trust semantics, longitudinal-memory trust semantics, and unrelated document content workflows.

# Ownership

- `api-bff` owns document type canonicalization, persisted document repair, and timeline/document projection behavior.
- `web-admin` owns display labels and document filtering based on the canonical document type returned by `api-bff`.
- Persistence remains in the clinical-document bounded context.

# Behavior

- A chest X-ray, CT, MRI, ultrasound, mammography, or equivalent imaging document must classify as radiology/imaging, not as an external lab report.
- Canonical radiology types must remain distinct from canonical laboratory types.
- If the upload request already supplies a radiology type, that type remains authoritative.
- If the upload request uses a lab-like or generic type but the document title/filename/description clearly identifies radiology, the canonical document type may be repaired to a radiology type.
- Laboratory reports such as CBC, CBC/CRP, lipid panels, thyroid panels, and metabolic panels must remain laboratory documents.
- Existing patient timeline and document list projections must reflect the canonical document type after repair.

# Compatibility

- No schema migration is required.
- Existing document records may be repaired forward-only when safely inferable.
- Existing laboratory report behavior must not regress.

# Validation

- Chest X-ray uploads surface as radiology/imaging in patient documents, patient timeline, and radiology filters.
- CBC/CBC-CRP/lipid/thyroid/metabolic documents remain laboratory documents.
- Unknown generic clinical documents do not automatically become laboratory documents.
- Existing Chest X-ray records can be safely repaired or reprojected without delete/re-upload.

# File ownership map

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/clinicaldocument/service/ClinicalDocumentService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/clinicaldocument/ClinicalDocumentController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/clinicalcontext/LongitudinalClinicalContextBuilder.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/clinicaldocument/service/ClinicalDocumentServiceTest.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/clinicaldocument/ClinicalDocumentControllerTimelineTest.java`
- `web-admin/src/components/clinical/documentTypeOptions.js`
- `web-admin/src/pages/patients/PatientDetailPage.tsx`
- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx`
- `web-admin/test/document-type-options.test.mjs`
