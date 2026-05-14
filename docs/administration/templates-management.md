# Templates Management v1

## Overview
Administration Templates provides tenant-scoped reusable content templates for operational messaging and future automation use-cases.

Route: `/admin/templates`

## Template Lifecycle
1. Create template (type/channel/category + subject/body).
2. Preview template using sample variable values.
3. Activate/deactivate for operational use.
4. Duplicate for safe edits.
5. Delete only non-system templates.

## Supported Variables
V1 supports simple placeholder replacement in `{{variableName}}` format.

Common examples:
- `{{patientName}}`
- `{{doctorName}}`
- `{{appointmentDate}}`
- `{{clinicName}}`
- `{{billAmount}}`
- `{{webinarLink}}`
- `{{leadName}}`

Unresolved placeholders remain unchanged in preview output to make missing variable mapping visible.

## Template Categories
- `APPOINTMENT_REMINDER`
- `REFILL_REMINDER`
- `BILLING`
- `WEBINAR`
- `FOLLOW_UP`
- `LEAD`
- `VACCINATION`
- `WELLNESS`
- `GENERAL`

## System Templates
- Created as tenant defaults when no system templates exist.
- Marked `systemTemplate=true`.
- Protected from deletion in API and UI.
- Can be activated/deactivated and edited by authorized roles.

## RBAC
- `CLINIC_ADMIN`: full management.
- `PLATFORM_ADMIN` / `PLATFORM_TENANT_SUPPORT`: full management with tenant context.
- `AUDITOR`: read-only.
- `RECEPTIONIST`: read-only.
- `DOCTOR`: denied (no nav visibility).
- `BILLING_USER`: denied in v1.

Backend authorization remains source of truth.

## Future Roadmap
- Explicit versioning and publish workflow.
- Multilingual templates.
- Variable catalog + strict validation rules by category.
- Template usage analytics and dependency graph.
- AI prompt/script template reuse for copilot and AI call flows.
