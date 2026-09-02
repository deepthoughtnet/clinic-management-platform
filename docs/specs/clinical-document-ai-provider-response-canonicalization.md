# Clinical Document AI Provider Response Canonicalization

## Scope

Introduce an internal adapter boundary between provider-shaped clinical document AI responses and the existing extraction quality, grounding, merge, and persistence pipeline.

## Ownership and placement

- Owning bounded context: clinical document AI application flow in `api-bff`
- API module: `backend/api/api-bff`
- Persistence owner: unchanged clinical document persistence
- Frontend: unchanged

The response adapter owns provider JSON path and field-shape handling. The extraction service remains responsible for deterministic OCR facts, source grounding, analyte canonicalization, quality-aware merge, confidence, review state, and persistence.

## Compatibility

Supported provider lab-result locations remain additive and ordered: `factualFindings.labResults`, root `labResults`, `answer.labResults`, `answer.extractedClinicalData.labResults`, `extractedClinicalData.labResults`, and existing legacy/classification shapes. No public API, schema, or migration changes are planned.

## Safety

Structured provider rows take precedence over narrative recovery only after normal validation. Serialized JSON is never treated as narrative. Deterministic OCR parsing remains independent. Existing incomplete, review-required, grounding, confidence, and longitudinal-memory gates remain authoritative.
