---
spec_id: JCP-CONSULTATION-AI-HISTORY-GAP-QUESTIONING
title: Consultation AI History Gap Questioning Guardrails
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: consultation
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Make the AI Assist "What else should I ask?" shortcut prioritize missing clinical history and clinician review items instead of administrative, billing, or internal AI metadata.

# Boundary

- In scope: consultation AI Assist shortcut handling for "What else should I ask?", prompt/context assembly, prompt template selection, and doctor-facing response wording.
- In scope: grounding suggestions in missing or unknown clinical history fields such as symptoms, red flags, allergies, medications, chronic conditions, past history, exposures, and examination/vitals gaps.
- In scope: separating patient questions from clinician-review items such as pending investigations.
- Out of scope: general consultation chat, diagnosis explanation, SOAP drafting, lab ordering, billing, and unrelated workflow prompts.

# Ownership

- `web-admin` owns the shortcut entry point and quick-prompt label.
- `api-bff` owns the narrow history-gap prompt assembly and AI orchestration for the response.
- `ai-domain` owns the consultation AI prompt template catalog and generation config mapping.

# Behavior

- The shortcut must behave as a clinical history-gap identification intent.
- Questions for the patient must prioritize missing history, red flags, medications, allergies, chronic conditions, past history, exposures, and vitals/examination gaps.
- If investigations matter, they must be presented separately as clinician-review items rather than patient questions.
- The prompt must not expose billing, payment, AI implementation details, prompt fragments, or truncated internal text.
- The prompt must not invent missing facts or contaminate one patient/consultation with another.

# Compatibility

- Existing generic consultation chat remains available for ordinary questions.
- The history-gap path is additive and must not change SOAP notes, advice, prescription, lab ordering, or diagnosis-explanation behavior.
- No schema or persistence migration is required.

# Validation

- Missing vitals/history/allergies/medications produce prioritized clinical follow-up questions.
- Complete clinical history produces a concise response with fewer missing-history gaps.
- Pending investigations appear only in a separate clinician-review section.
- No diagnosis recorded remains explicit rather than inferred.
- No billing or internal AI metadata appears in the response or prompt context.
- Cross-patient and cross-consultation contamination is not allowed.

# File ownership map

- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/service/AiConsultationAskService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/service/AiDoctorCopilotService.java`
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/orchestration/platform/service/AiTaskGenerationConfigService.java`
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/orchestration/service/impl/AiPromptTemplateCatalog.java`
