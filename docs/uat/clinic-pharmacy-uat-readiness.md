# Clinic + Pharmacy UAT Readiness

## Completed Modules

- Clinic appointments, consultations, prescriptions, billing, payments, and refunds
- Pharmacy medicine master, inventory, dispensing, reconciliation, procurement, GRN, and stock movements
- Pharmacy POS sale, FEFO stock allocation, stock deduction, receipts, returns, prescription upload/scan, barcode/QR scan, and cashier shifts
- Finance reports with clinic + pharmacy revenue, daily sales, medicine sales, payment modes, cashier shifts
- Unified Cash Counter read-only finance view across clinic billing and pharmacy POS collections, refunds, returns, and shifts

## Validated Pharmacy Flows

- Medicine search and hardware barcode entry
- Camera barcode and QR scan into POS
- FEFO allocation with expired batch exclusion
- Multi-batch sale deduction and movement audit
- Payment collection with open cashier shift requirement
- Partial and full return with reusable and discard behavior
- Prescription upload and camera capture with tenant-scoped storage
- Stock inward, sale, return, reconciliation, and purchase movement visibility

## Final E2E Checklist

1. Login as `BILLING_USER` and verify `Billing`, `Cash Counter`, and `Reports`.
2. Login as `PHARMACIST` and verify `Pharmacy POS`, `Inventory`, `Dispensing`, and no finance-only pages.
3. Create a clinic bill and collect payment.
4. Create a pharmacy POS sale and collect payment under an open shift.
5. Process a pharmacy return and confirm stock and refund audit.
6. Open `Cash Counter` and verify combined clinic + pharmacy ledger rows.
7. Open `Reports` and verify `Revenue`, `Daily Sales`, `Medicine Sales`, `Payment Modes`, and `Cashier Shifts`.
8. Export CSV from `Cash Counter` and `Reports`.
9. Verify `Doctor` and `Receptionist` cannot access finance reports or cash counter.
10. Verify tenant switching does not reveal another tenant’s finance or prescription data.

## Test Users / Roles

- `CLINIC_ADMIN`
- `BILLING_USER`
- `PHARMACIST` or `PHARMACY`
- `DOCTOR`
- `RECEPTIONIST`
- `PLATFORM_ADMIN` with tenant selected

## Suggested UAT Data

- Medicines:
  - `Paracetamol 500`
  - `Amoxicillin 500`
  - `Cetirizine 10`
- Suppliers:
  - `Medline Distributors`
  - `HealthHub Supply`
- Batches:
  - `PARA-B001`
  - `PARA-B002`
  - `AMOX-B001`
- PO / Invoice / GRN:
  - `PO-1001`
  - `INV-2001`
  - `GRN-3001`
- Pharmacy POS:
  - one paid sale
  - one unpaid sale
  - one partial return
- Billing:
  - one fully paid bill
  - one partial refund

## Known Limitations

- Unified cash counter is a reporting and lookup layer, not a replacement source-of-truth ledger.
- Clinic billing and pharmacy POS still use separate receipt numbering sequences.
- A true central `cashier_transactions` table is documented but not implemented in this batch.
- Some older non-finance report tabs still expose operational identifiers outside the new cash-counter view and may need further UI normalization if those tabs are expanded for broader user groups.

## Pending Production Items

- Introduce tenant-scoped unified receipt numbering across clinic billing and pharmacy POS
- Add central cashier transaction ledger if finance ops require a single accounting source
- Add browser-level manual UAT execution record with screenshots and sign-off
