---
spec_id: JCP-BILLING-RECEIPT-PDF-SEPARATION
title: Billing Receipt Canonical Template
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: billing-domain
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Make billing receipt preview, browser print, and PDF download reuse a single canonical receipt template so every rendering path produces the same business document.

# Boundary

- In scope: billing ledger receipt actions, receipt preview dialog, browser print flow, direct PDF download flow, receipt filename selection, and failure messaging.
- In scope: reuse of the same receipt rendering model for preview, print, and backend PDF generation.
- Out of scope: changes to receipt business rules or receipt content.
- Out of scope: destructive migration or storage changes.

# Ownership

`billing-domain` owns the canonical receipt model and PDF generation.

`api-bff` owns the receipt PDF HTTP endpoint and response headers.

`web-admin` owns the billing ledger action wiring, download behavior, and user-visible error handling.

# Behavior

- View Receipt opens the receipt preview dialog rendered from the canonical receipt model.
- Print Receipt opens the browser print flow from the same canonical receipt content.
- Download Receipt PDF fetches the PDF directly from the same canonical receipt model and downloads it without opening preview or print UI.
- Downloaded filenames must be business friendly.
- Preferred filename format is `Receipt-{ReceiptNumber}.pdf`.
- If a receipt number is unavailable, the bill number may be used as the fallback.
- Download failures must surface a visible error message.

# Compatibility

- Receipt preview, print, and PDF download use one canonical receipt template.
- Legacy receipt HTML/template and obsolete branding must not remain in the system for receipts.
- Existing backend receipt PDF generation is replaced with the canonical receipt renderer.
- No existing migration is renamed or modified.

# Validation

- Verify the ledger menu bindings point to separate handlers.
- Verify receipt preview still opens only from View Receipt.
- Verify Print Receipt still invokes the browser print flow.
- Verify Download Receipt PDF downloads the blob directly and uses the business-friendly filename.
- Verify PDF text contains the same bill, receipt, patient, payment, and clinic data as preview.
- Verify failure handling shows an error message when PDF generation fails.

# Layout hardening

- Receipt preview, print, and PDF download continue to represent one canonical receipt.
- The receipt header uses the configured clinic branding source and omits the logo cleanly when unavailable.
- Receipt metadata must be layout-aware so wrapped appointment text cannot overlap the amount row.
- Decorative header patches are not part of the canonical receipt document.

# File ownership map

- `web-admin/src/pages/billing/BillsPage.tsx`
- `web-admin/src/api/clinicApi.ts` if receipt PDF client behavior changes in a later batch
- `web-admin/test/receipt-canonical-renderer.test.mjs`
- `web-admin/test/workflow-ui-wiring.test.mjs` if additional source assertions are needed
