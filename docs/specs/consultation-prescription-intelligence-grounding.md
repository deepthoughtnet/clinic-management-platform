---
spec_id: JCP-CONSULTATION-PRESCRIPTION-INTELLIGENCE-GROUNDING
title: Consultation Prescription Intelligence Grounding and Safety Guardrails
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: consultation
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Prevent smart medicine suggestions from attributing unrelated symptoms, allergies, or medicine interactions to the current consultation. Prescription suggestions must remain grounded in the current tenant, patient, and consultation context and must not reuse prior-consultation or fallback context as if it were current encounter data.

# Boundary

- In scope: the doctor-facing "Suggest medicines with AIVA" flow, backend context assembly, prompt template wording, AI parsing/normalization, and safety-note rendering for medication suggestions.
- In scope: preservation of manual doctor review actions, Accept/Edit/Reject flows, and prescription persistence rules.
- Out of scope: generic consultation chat, explain-diagnosis, history-gap, investigation-suggestion, medication safety review, or unrelated prescription refactoring.

# Ownership

- `web-admin` owns the prescription-intelligence UI and the request payload sent for smart medicine suggestions.
- `api-bff` owns the grounded context assembly and validation for prescription suggestions.
- `ai-domain` owns the prescription suggestion prompt template and orchestration defaults.

# Behavior

- Prescription suggestions must be grounded only in the current tenant + patient + consultation context.
- The prescription suggestion flow must not use previous consultations, longitudinal memory, template examples, or fallback clinical indications as though they belong to the current encounter.
- If current context is insufficient, the system should surface no recommendation or a clearly conditional/insufficient-information response.
- A patient-specific allergy or interaction warning may only be shown when the underlying structured patient data supports it.
- Generic or conditional medication-safety language must remain clearly conditional when the current record does not contain the relevant allergy or medicine.
- AI-generated content must not auto-enter the prescription draft without doctor review.

# Compatibility

- Accept / Edit / Reject / Copy controls remain available.
- Manual prescribing remains available.
- No lab or prescription request is created by AI generation alone.
- No schema or persistence migration is required.

# Validation

- URI/back-pain contamination does not appear in a viral URI consultation.
- Sequential patients and sequential consultations do not contaminate one another.
- Allergy absent => no fabricated allergy assertion.
- Allergy explicitly present => warning may be shown.
- Current medicines absent => no patient-specific interaction claim.
- Current medicine explicitly present => relevant interaction may be shown.
- AI rationale attaches only to grounded suggestions.
- The final reviewed prescription payload remains doctor-controlled.

# File ownership map

- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx`
- `web-admin/test/prescription-intelligence.test.mjs`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/service/AiConsultationDraftService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/clinicalcontext/ClinicalContextService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/medicationsafety/MedicationSafetyService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/medicationsafety/MedicationSafetyEngine.java`
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/orchestration/service/impl/AiPromptTemplateCatalog.java`
