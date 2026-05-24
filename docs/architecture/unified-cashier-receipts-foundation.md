# Unified Cashier And Receipt Foundation

## Status

This is a forward-looking design note only.

UAT status as of May 24, 2026:
- unified cash counter page is available as a read-only additive finance view
- existing clinic billing and pharmacy POS receipt numbering remain unchanged
- no shared tenant-scoped receipt sequence has been introduced in this batch

Current modules remain unchanged:
- clinic billing payments
- pharmacy POS payments
- pharmacy refunds / returns
- cashier shifts
- receipt generation

No backend refactor is implied by this document.

## Problem

Cash collection is currently split across multiple workflows:
- clinic bills
- pharmacy POS sales
- pharmacy POS refunds
- cashier shift summaries

That creates duplication in:
- receipt numbering
- payment audit trails
- cashier reconciliation
- reporting by payment mode

## Proposed Future Direction

Introduce a tenant-scoped central cashier ledger.

Suggested table:
- `cashier_transactions`

Suggested fields:
- `id`
- `tenant_id`
- `receipt_number`
- `source_type`
- `source_id`
- `payment_mode`
- `amount`
- `cashier_shift_id`
- `collected_by`
- `created_at`

Suggested `source_type` values:
- `CLINIC_BILL`
- `PHARMACY_POS`
- `REFUND`
- `ADVANCE`

## Unified Receipt Numbering

Future receipts should use one tenant-scoped sequence regardless of source.

A unified receipt should show:
- source type
- patient or walk-in customer
- payment mode
- cashier
- shift reference
- amount
- refund or return reference when applicable

## Migration Strategy

Recommended phased approach:
1. Keep current billing and pharmacy POS payment tables as source-of-truth.
2. Introduce `cashier_transactions` as a parallel audit ledger.
3. Mirror new bill payments, POS payments, and refunds into the central ledger.
4. Switch receipt rendering to consume unified receipt metadata.
5. Only later consider consolidating domain-specific payment tables if the ledger proves stable.

## Constraints

Any future implementation must preserve:
- tenant isolation
- existing RBAC
- maker-checker and audit trails
- cashier shift linkage
- backward compatibility for existing receipt numbers and reports
