# Pharmacy Medicine Master Validation Parity

## Scope

Validate and harden the Pharmacy Medicine Master implementation across:

- `web-admin` Medicine Master form and CSV preview
- `api-bff` medicine endpoints and CSV import orchestration
- `inventory-domain` medicine persistence and duplicate handling
- supporting automated tests

## Owner

- Bounded context: `inventory`
- API module: `backend/api/api-bff`
- Persistence module: `backend/domains/inventory-domain`
- Frontend area: `web-admin/src/pages/pharmacy/MedicineMasterPage.tsx`

## Goals

- Manual create/update and CSV import must enforce equivalent medicine validation rules.
- Validation must be tenant-scoped.
- Duplicate handling must remain user-friendly and consistent with the supported medicine identity.
- Backend validation must remain authoritative even if frontend validation is bypassed.
- Existing pharmacy/inventory UX should remain intact unless a validation/data-integrity gap is being closed.

## Validation contract

Supported fields must be trimmed before persistence.

Required:

- `medicineName`
- `medicineType`
- `strength`
- `active`

Optional:

- `barcode`
- `qrCode`
- `externalCode`
- `genericName`
- `brandName`
- `category`
- `dosageForm`
- `unit`
- `manufacturer`
- `defaultDosage`
- `defaultFrequency`
- `defaultDurationDays`
- `defaultTiming`
- `defaultInstructions`
- `defaultPrice`
- `taxRate`

## Duplicate rule

The supported medicine identity is tenant-scoped and uses:

- medicine name
- medicine type
- strength

Barcode and external code remain independently unique where populated.

## CSV behavior

- Template headers must match supported import columns.
- Import should continue on row-level failures.
- Duplicate rows inside the file must be skipped or rejected without crashing the batch.
- Existing catalogue duplicates must be reported as user-facing row errors.

## Validation strategy

- Keep frontend validation and backend validation aligned.
- Reuse shared validation where practical.
- Preserve current audit logging and tenant isolation.

## Non-goals

- No redesign of the Medicine Master UI.
- No unrelated pharmacy workflow changes.
- No broader inventory architecture changes.

## Completion checks

- Backend validation rejects invalid manual and CSV payloads.
- Backend duplicate handling allows legitimate name/strength variants.
- CSV import reports row-level errors cleanly.
- Tests cover boundary and duplicate cases.
