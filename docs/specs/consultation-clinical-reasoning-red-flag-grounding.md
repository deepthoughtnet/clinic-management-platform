# Clinical Reasoning Red-Flag Grounding

## Owner

Consultation bounded context, exposed through `backend/api/api-bff` clinical reasoning endpoints and rendered in `web-admin` consultation workspace.

## Problem Statement

Clinical reasoning output can surface diabetes-specific red flags even when diabetes is not present in structured patient/context data. The workflow must never present unsupported conditions as facts about the patient.

## In Scope

- Clinical reasoning prompt wording for patient-specific red flags
- Backend grounding/sanitization of red-flag wording
- Clinical reasoning tests for no-diabetes, diabetes-present, and cross-patient isolation cases
- Existing consultation UI rendering of returned red flags

## Out of Scope

- Consultation lifecycle
- Doctor control / acceptance workflow
- Audit metadata
- Non-reasoning consultation features
- Other Engage/Pharmacy/Lab/Finance workflows

## Compatibility / Safety

- Preserve clinically useful warnings such as breathlessness, low SpO2, prolonged fever, and altered sensorium.
- Do not weaken or remove supported safety warnings.
- Do not add new persistence or schema changes.
- Do not introduce cross-patient state sharing.

## Implementation Notes

- Use only recorded patient/context data to decide whether a diabetes-specific warning can be stated as factual.
- If a diabetes-linked risk is generated without structured diabetes context, rephrase it conditionally.
- Keep the consultation UI as a passthrough renderer unless additional metadata is required later.

## Validation / Tests

- No diabetes in context: red flags must not assert diabetes as a fact.
- Diabetes explicitly recorded: diabetes-specific warning may appear.
- Unknown/missing condition: generic diabetes-linked risk must render conditionally.
- Separate patient/consultation contexts must not leak red-flag state.

## File Ownership

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/ai/reasoning/*`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/ai/reasoning/*`
- `web-admin/src/pages/consultations/ConsultationWorkspacePage.tsx` only if renderer adjustments become necessary
