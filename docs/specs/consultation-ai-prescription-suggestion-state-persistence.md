---
spec_id: JCP-CONSULTATION-AI-PRESCRIPTION-SUGGESTION-STATE-PERSISTENCE
title: Consultation AI Prescription Suggestion State Persistence
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: consultation
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Persist doctor-facing smart medicine suggestion state so generated prescription suggestions survive normal auto-save, page refresh, and consultation reloads without losing the current suggestion set or the doctor review decision state.

# Boundary

- In scope: the doctor-facing "Suggest medicines with AIVA" flow, consultation-scoped persistence, versioning/supersession, suggestion-item review states, stale detection, and reload hydration.
- In scope: preservation of manual doctor review actions, Accept/Reject/edited suggestion states where supported, and prescription persistence rules.
- Out of scope: generic consultation chat, explain-diagnosis, history-gap, investigation-suggestion, medication safety review, and unrelated prescription refactoring.

# Ownership

- `consultation-domain` owns the persisted consultation AI prescription-suggestion record and its version history.
- `api-bff` owns the transport DTOs, consultation-scoped orchestration, stale detection, and audit publication.
- `web-admin` owns the prescription-suggestion UI state, hydration, and persistence calls.

# Behavior

- Generated prescription suggestions must survive normal consultation auto-save and browser refresh for the same consultation.
- The latest suggestion set is current; earlier suggestion sets are retained and marked superseded rather than deleted.
- Review states for each suggestion item must survive reloads.
- A doctor-accepted suggestion must remain distinguishable from manual prescription rows and remain auditable.
- When the current consultation context changes materially, the persisted suggestion set must be marked stale/superseded rather than silently presented as current.
- AI generation must not auto-enter the prescription without doctor review.

# Compatibility

- Accept / Reject / Copy controls remain available.
- Manual prescribing remains available.
- No lab or prescription request is created by AI generation alone.
- The implementation is forward-only and must preserve existing consultation, prescription, and medication-safety behavior.

# Validation

- Generation survives auto-save.
- Generation survives browser/page refresh.
- Rejection survives refresh.
- Acceptance survives refresh.
- Accepted medicine reaches the prescription exactly once.
- Manual prescription save does not clear AI state.
- Regeneration supersedes the previous suggestion set.
- Context change marks the previous suggestion set stale/superseded.
- No cross-consultation leakage.

# File ownership map

- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx`
- `web-admin/src/api/clinicApi.ts`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/consultation/ConsultationController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/consultation/service/ConsultationAiPrescriptionSuggestionService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/consultation/service/ConsultationAiPrescriptionSuggestionContextHasher.java`
- `backend/domains/consultation-domain/src/main/java/com/deepthoughtnet/clinic/consultation/db/ConsultationAiPrescriptionSuggestionEntity.java`
- `backend/domains/consultation-domain/src/main/java/com/deepthoughtnet/clinic/consultation/db/ConsultationAiPrescriptionSuggestionRepository.java`
