---
spec_id: JCP-CONSULTATION-AI-INVESTIGATION-SUGGESTION
title: Consultation AI Investigation Suggest Tests Guardrails
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: consultation
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Make the AI Assist "Suggest tests" shortcut provide clinically grounded investigation guidance instead of leaking administrative, billing, or internal AI metadata.

# Boundary

- In scope: consultation AI Assist shortcut handling for "Suggest tests", prompt/context assembly, prompt template selection, and doctor-facing investigation guidance wording.
- In scope: grounding suggestions in current symptoms, diagnosis, red flags, history, missing data, and already ordered/pending/available investigations.
- In scope: explicit separation of already ordered/pending tests, tests to consider if indicated, and situations where there is insufficient information for a specific recommendation.
- Out of scope: generic consultation chat, diagnosis explanation, history-gap questioning, consultation lab order creation, lab-catalog mapping, and unrelated consultation workflows.

# Ownership

- `web-admin` owns the shortcut entry point and quick-prompt label.
- `api-bff` owns the narrow investigation-suggestion prompt assembly and AI orchestration for the response.
- `ai-domain` owns the consultation AI prompt template catalog and generation config mapping.

# Behavior

- The shortcut must behave as a clinical investigation-support intent.
- Already ordered, pending, and recently available investigations must be identified first so the response can avoid duplicates.
- Suggested tests must be clinically justified by the current consultation context and must include a brief rationale.
- If evidence is insufficient for a specific test, the response must say so instead of inventing one.
- Billing, payment, internal AI metadata, prompt fragments, and implementation details must not appear in the prompt or response.
- The shortcut must not auto-select, create, or map a catalog test.

# Compatibility

- Existing generic consultation chat remains available for ordinary questions.
- The investigation-suggestion path is additive and must not change diagnosis explanation, history-gap questioning, SOAP notes, or lab-order mapping behavior.
- No schema or persistence migration is required.

# Validation

- Existing pending CBC or similar tests are called out before any new suggestion and are not duplicated without a clear clinical reason.
- No existing investigations yields a clinically grounded suggestion or an explicit statement that evidence is insufficient.
- Red-flag symptoms can justify additional tests with brief rationale.
- Ambiguous or incomplete presentations do not fabricate unsupported test recommendations.
- No billing or internal AI metadata appears in the response or prompt context.
- Cross-patient and cross-consultation contamination is not allowed.

# File ownership map

- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/service/AiConsultationAskService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/service/AiDoctorCopilotService.java`
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/orchestration/platform/service/AiTaskGenerationConfigService.java`
- `backend/domains/ai-domain/src/main/java/com/deepthoughtnet/clinic/ai/orchestration/service/impl/AiPromptTemplateCatalog.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/ai/service/AiConsultationAskServiceTest.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/ai/service/AiDoctorCopilotServiceTest.java`
- `backend/domains/ai-domain/src/test/java/com/deepthoughtnet/clinic/ai/orchestration/platform/service/AiTaskGenerationConfigServiceTest.java`
- `backend/domains/ai-domain/src/test/java/com/deepthoughtnet/clinic/ai/orchestration/service/impl/AiPromptTemplateCatalogTest.java`
