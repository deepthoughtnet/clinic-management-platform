---
spec_id: JCP-CONSULTATION-AI-LAB-INVESTIGATION-MAPPING
title: Consultation AI Lab Investigation Mapping Guardrails
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: consultation
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Prevent clinical reasoning recommendations from being attributed to the wrong lab catalog test when the consultation workspace prepares a laboratory order. Only deterministic and grounded mappings may auto-select a catalog item.

# Boundary

- In scope: consultation workspace AI investigation presentation, recommendation-to-lab-catalog mapping, auto-selection gating, and doctor-review order preparation.
- In scope: transparent display of unmapped or ambiguous recommendations so the doctor can choose a catalog test manually.
- In scope: test payloads for the consultation lab order flow and preservation of existing manual search/selection behavior.
- Out of scope: laboratory order lifecycle, lab pricing, lab catalog persistence, duplicate checking, and unrelated consultation workflows.

# Ownership

- `web-admin` owns the consultation workspace interaction model and the modal state for preparing a lab order from AI recommendations.
- `api-bff` remains the source of truth for consultation clinical reasoning output and lab order creation validation.

# Behavior

- A recommendation may auto-select a catalog test only when the mapping is deterministic and uniquely grounded.
- If a recommendation maps to more than one catalog candidate, it is shown as ambiguous and does not auto-select anything.
- If a recommendation does not map safely to a catalog item, it remains visible as an unmapped recommendation and the doctor must select a test manually.
- Mandatory/default catalog tests must never inherit AI rationale merely because they are present in the catalog or included in the workspace.
- The final review/create payload contains only the doctor-reviewed selected test IDs.

# Compatibility

- Existing doctor review and order confirmation behavior remains available.
- No lab request is created by AI generation alone.
- No schema or persistence migration is required.

# Validation

- Exact catalog match auto-selects the unique catalog item.
- Alias-based mapping auto-selects only when the alias resolves to one catalog item.
- Ambiguous recommendation remains unmapped and does not auto-select.
- No-match recommendation remains unmapped.
- AI rationale attaches only to a genuinely mapped recommendation.
- The final lab order payload contains only selected test IDs that remain under doctor control.

# File ownership map

- `web-admin/src/pages/consultations/labRecommendationMatcher.js`
- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx`
- `web-admin/test/consultation-lab-investigation-mapping.test.mjs`
