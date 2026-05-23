# Pharmacy E2E Test Data

Use this compact dataset for repeatable local or UAT pharmacy workflow checks. Keep all records inside the same tenant.

## Medicines

| Medicine | Type | Strength | Barcode | External Code |
| --- | --- | --- | --- | --- |
| Paracetamol 500 | TABLET | 500 mg | 890100000001 | PARA-500 |
| Amoxicillin 500 | CAPSULE | 500 mg | 890100000002 | AMOX-500 |
| Cetirizine 10 | TABLET | 10 mg | 890100000003 | CET-10 |

## Suppliers

| Supplier | GSTIN | Contact |
| --- | --- | --- |
| Apex Pharma Distributors | 29ABCDE1234F1Z5 | 9876543210 |
| Sunrise Medisupply | 29PQRSX5678K1Z2 | 9988776655 |

## Stock Batches

| Medicine | Location | Batch | Barcode | Expiry | Qty | Unit Cost | Selling Price |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Paracetamol 500 | Main Pharmacy | PARA-B001 | 890100000001 | 2026-09-30 | 120 | 0.85 | 1.50 |
| Paracetamol 500 | Main Pharmacy | PARA-B002 | 890100000001 | 2027-01-31 | 180 | 0.88 | 1.55 |
| Amoxicillin 500 | Main Pharmacy | AMOX-B001 | 890100000002 | 2026-11-30 | 90 | 3.50 | 5.20 |
| Cetirizine 10 | Main Pharmacy | CET-B001 | 890100000003 | 2026-08-31 | 60 | 0.70 | 1.20 |

## Procurement References

| Workflow | Reference |
| --- | --- |
| Purchase Order | PO-APR-1001 |
| Supplier Invoice | INV-APR-2048 |
| Goods Receipt | GRN-APR-2048 |

## Reconciliation Draft

Use supplier `Apex Pharma Distributors`, location `Main Pharmacy`, and primary medicine `Paracetamol 500`.

Sample vendor-sheet rows:

| Row | Medicine | Batch | Qty | Expiry | Expected Review Outcome |
| --- | --- | --- | --- | --- | --- |
| 1 | Paracetamol 500 | PARA-B001 | 118 | 2026-09-30 | Variance review |
| 2 | Paracetamol 500 | PARA-B002 | 180 | 2027-01-31 | Accept |
| 3 | Unknown Fever Mix | UFM-01 | 24 | 2026-12-31 | Missing medicine handling |

## POS Sale and Return

| Workflow | Details |
| --- | --- |
| POS sale | Walk-in customer `Rahul Counter`, mobile `9999999999`, one Paracetamol 500 line, one Amoxicillin 500 line |
| Partial return | Return 1 Paracetamol unit as reusable |
| Non-reusable return | Return 1 damaged Cetirizine unit as discard |

## Shift Session

Open cashier shift with:

- Opening cash: `1000`
- Cash sale: `100`
- UPI sale: `200`
- Card sale: `300`

Expected close totals:

- Cash: `100`
- UPI: `200`
- Card: `300`
- Other: `0`

## E2E Coverage Map

- Supplier create/search: `Apex Pharma Distributors`
- PO create: `PO-APR-1001`
- Invoice create: `INV-APR-2048`
- GRN create/confirm: `GRN-APR-2048`
- Reconciliation draft/review/post: `Paracetamol 500` batches
- Inventory duplicate-batch check: retry `PARA-B001` in `Main Pharmacy`
- FEFO POS check: sell `Paracetamol 500` and confirm `PARA-B001` is used before `PARA-B002`
