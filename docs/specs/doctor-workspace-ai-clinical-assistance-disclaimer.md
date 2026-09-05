---
spec_id: JCP-DOCTOR-WORKSPACE-AI-CLINICAL-ASSISTANCE-DISCLAIMER
title: Doctor Workspace AI Clinical Assistance Disclaimer
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: doctor-workspace
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Provide a persistent doctor-facing AI assistance disclaimer in the Doctor Workspace shell without changing clinical workflows or AI logic.

# Boundary

- In scope: doctor-facing shell/footer disclosure, compact disclaimer text, and a full disclaimer dialog/drawer.
- In scope: role-gated presentation within doctor workspace routes.
- Out of scope: AI prompts, AI provider behavior, diagnosis logic, prescription logic, findings review, longitudinal memory, and other clinical workflows.

# Ownership

- `web-admin` owns the doctor workspace footer presentation and disclosure interaction.
- Backend changes are not required.

# Behavior

- Doctor workspace routes show a compact persistent AI assistance disclaimer.
- The compact disclaimer includes a link/action to open the full disclaimer.
- The full disclaimer explains AI is assistive only and clinician review remains mandatory.
- Non-doctor routes must not receive the doctor-specific disclaimer if the shell is shared.
- Existing inline AI warnings remain unchanged.

# Validation

- Doctor workspace routes render the compact disclaimer in the shell footer.
- The full disclaimer opens and closes with keyboard-accessible dialog behavior.
- Non-doctor routes do not render the doctor-specific disclaimer.
- Existing AI warning text elsewhere in the workspace remains present.
