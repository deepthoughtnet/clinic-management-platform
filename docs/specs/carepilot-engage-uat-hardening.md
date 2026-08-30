# Jeevanam Engage UAT Hardening

## Scope
- Engage login/default routing
- Lead creation/editing and assignee selection
- Follow-up scheduling validation
- Converted lead terminal protection
- Lead timeline UX and audit detail clarity
- Messaging validation UX and readiness guidance
- Consent / opt-out traceability
- Campaign preset application behavior

## Confirmed behavior
- Engage executive and manager users must land on an authorized Engage page.
- Lead first name remains mandatory and must show a required marker.
- Assignee selection must be limited to active Engage-eligible users.
- Follow-up scheduling must reject past date/time values in tenant time.
- Converted leads are read-only for operational users.
- Messaging test send must not remain enabled when validation fails.
- Campaign presets must not silently overwrite user-entered trigger/audience choices.

## Validation rules
- Backend authorization remains authoritative for lead updates and assignee eligibility.
- Terminal lead state changes are rejected server-side.
- Tenant-scoped consent or notification policy must be exposed using the existing source of truth only.
- No secret or environment implementation detail should be shown to ordinary tenant users.

