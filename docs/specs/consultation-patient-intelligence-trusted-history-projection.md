---
spec_id: JCP-CONSULTATION-PATIENT-INTELLIGENCE-TRUSTED-HISTORY-PROJECTION
title: Consultation Patient Intelligence Trusted History Projection
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: consultation
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Ensure completed AI findings review becomes visible in Patient Intelligence as trusted longitudinal history, while partially reviewed findings remain excluded from trusted projections until review completion.

# Boundary

- In scope: consultation Patient Intelligence rendering, trusted-history visibility, and the consultation-side interpretation of longitudinal memory history.
- In scope: display of completed review-derived findings as trusted longitudinal context.
- Out of scope: extraction/parser/provider behavior, finding-review workflows, longitudinal trust transition semantics, secure document preview, and Doctor AI intents.

# Ownership

- `api-bff` owns the longitudinal memory projection consumed by Patient Intelligence.
- `web-admin` owns the consultation Patient Intelligence presentation and empty-state gating.
- Persistence and review metadata remain in the clinical-memory and clinical-document bounded contexts.

# Behavior

- Completed AI findings review must surface trusted findings in Patient Intelligence.
- Pending or incomplete reviews must remain excluded from trusted longitudinal history.
- Trusted history may be rendered as a compact verified finding list when no narrower summary bucket exists.
- The consultation card must not show an empty-state message when trusted longitudinal facts are available.
- Review metadata and longitudinal trust remain separate concerns.

# Compatibility

- No review action semantics change.
- No extraction or parser change is required.
- No schema migration is required for this visibility fix.

# Validation

- Complete review of a lab report makes trusted findings visible in Patient Intelligence.
- Partially reviewed reports do not appear in trusted longitudinal history.
- Patient Intelligence continues to show pending-review cues separately from trusted history.
- Existing consultation navigation and review entry points remain unchanged.

