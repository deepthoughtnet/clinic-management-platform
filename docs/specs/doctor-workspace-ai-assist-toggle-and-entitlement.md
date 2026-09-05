---
spec_id: JCP-DOCTOR-WORKSPACE-AI-ASSIST-TOGGLE-AND-ENTITLEMENT
title: Doctor AI Assist Toggle and AI Copilot Entitlement Enforcement
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: doctor-workspace
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Provide two separate controls for Doctor Workspace AI behavior:

- tenant-level AI_COPILOT entitlement, which determines whether AI exists at all for the clinic
- doctor-level AI Assist on/off preference, which determines whether an entitled doctor wants to use AI in the current workspace

# Boundary

- In scope: doctor workspace AI visibility, doctor preference persistence, persistent footer disclosure, and server-side enforcement of tenant AI_COPILOT entitlement for doctor AI APIs.
- In scope: consultative doctor workspace AI entry points such as Clinical Reasoning, Suggest Tests, AI Assist, and AI prescription assistance.
- Out of scope: AI prompts, provider/model configuration, prescription logic, finding-review workflow, longitudinal trust semantics, and patient data storage.
- Clinical Document AI extraction/review remains a separate capability unless a distinct entitlement already governs it.

# Ownership

- `web-admin` owns the doctor workspace AI toggle presentation and local preference persistence.
- `api-bff` owns server-side enforcement of tenant AI_COPILOT access for doctor AI endpoints.

# Behavior

- If the tenant does not have AI_COPILOT, doctor AI surfaces are hidden and AI endpoints are denied server-side.
- If the tenant has AI_COPILOT and the doctor turns AI Assist off, the workspace remains fully usable manually and AI-specific controls are hidden or disabled consistently.
- Turning AI Assist off does not delete previously accepted/reviewed AI-derived clinical state.
- The persistent doctor AI disclaimer is shown only when AI features are available for the workspace.
- The AI Assist preference is stored per doctor and tenant in browser-local preference storage unless a shared preference model already exists.

# Validation

- Entitled doctor with AI Assist on sees AI entry points and can invoke AI endpoints.
- Entitled doctor with AI Assist off sees manual workflow and an explicit off state.
- Non-entitled tenant sees no doctor AI controls and cannot call doctor AI endpoints directly.
- Previously accepted/reviewed AI-derived clinical data remains available after AI Assist is turned off.
- Non-doctor workspaces do not receive the doctor-specific disclaimer or toggle.
