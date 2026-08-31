---
spec_id: JCP-CONSULTATION-AI-DIAGNOSIS-EXPLANATION
title: Consultation AI Diagnosis Explanation Guardrails
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: consultation
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Make the AI Assist "Explain diagnosis" shortcut explain the doctor-recorded working diagnosis instead of regurgitating the entire consultation context.

# Boundary

- In scope: consultation AI Assist shortcut handling for "Explain diagnosis", prompt/context assembly, prompt template selection, and doctor-facing response wording.
- In scope: clinical grounding rules that keep the response tied to recorded patient facts and clearly mark missing or uncertain information.
- In scope: tests that verify the shortcut is diagnosis-focused, does not leak unsupported patient facts, and remains isolated from other consultation AI prompts.
- Out of scope: clinical reasoning engine refactors, SOAP drafting, prescription generation, lab ordering, and unrelated clinical chat features.

# Ownership

- `web-admin` owns the shortcut entry point and the user-visible quick-prompt label.
- `api-bff` owns the actual prompt assembly, consultation context selection, and AI orchestration for the explanation response.

# Behavior

- The shortcut must explain the current working diagnosis, not present it as a confirmed fact.
- The response must mention supporting symptoms/findings and important alternatives only when they are grounded in the consultation context.
- Missing vitals, history, examination, or pending investigations must be called out when they materially affect certainty.
- The response must not repeat unrelated consultation logistics, billing, or full chart summaries.
- If no diagnosis is recorded, the response must say so clearly rather than inventing one.

# Compatibility

- Existing generic consultation chat remains available for ordinary questions.
- The diagnosis explanation path is additive and must not change SOAP notes, advice, or prescription workflows.
- No schema or persistence migration is required.

# Validation

- Exact "Explain diagnosis" shortcut routes to a diagnosis-focused prompt path.
- The prompt includes only grounded diagnostic context and avoids unsupported patient facts.
- Recorded diagnosis plus sufficient context yields a clinical explanation with supporting evidence.
- Recorded diagnosis with missing context explicitly mentions missing information.
- No recorded diagnosis produces a safe "no diagnosis recorded" response.
- Cross-patient and cross-consultation context leakage is not allowed.

# File ownership map

- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/service/AiConsultationAskService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/service/AiDoctorCopilotService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/reasoning/ClinicalReasoningPromptBuilder.java` (for adjacent grounding reference only if needed by tests)
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/orchestration/platform/service/AiTaskGenerationConfigService.java`
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/orchestration/service/impl/AiPromptTemplateCatalog.java`
