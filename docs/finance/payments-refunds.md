# Payments and Refunds

## Payment Lifecycle
- Bills are created and issued from the Billing module.
- Payments are recorded against a bill using existing `POST /api/bills/{billId}/payments`.
- Bill status and due amount continue to be calculated by existing billing-domain logic.
- Receipt artifacts and receipt notifications continue through existing receipt and notification endpoints.

## Refund Lifecycle
- Refunds are issued against a bill using existing `POST /api/bills/{billId}/refunds`.
- Refund execution and bill financial recomputation remain in billing-domain service logic.
- Partial and full refund outcomes are reflected through existing bill status handling.

## Finance Operational Pages
- `Payments` page (`/finance/payments`) provides cross-bill operational tracking:
  - filter by date range, payment mode, bill number, and patient/bill search
  - open bill workflow handoff
  - receipt email/download actions when receipt is available
- `Refunds` page (`/finance/refunds`) provides cross-bill refund operations:
  - filter by date range, refund mode, bill number, and patient/bill search
  - open bill workflow handoff
  - issue refund action for authorized roles

## Role Behavior
- `CLINIC_ADMIN`: full payments/refunds visibility and actions.
- `BILLING_USER`: payments/refunds visibility and operational actions per backend permissions.
- `RECEPTIONIST`: navigation visibility; backend permissions remain authoritative for mutation access.
- `AUDITOR`: read-focused usage through existing `billing.read` style permissions.
- `DOCTOR`: finance navigation not exposed in this module set.
- `PLATFORM_ADMIN`: tenant context required by request context enforcement.

## Relationship With Billing Page
- Billing remains the authoritative workspace for detailed bill lifecycle operations.
- Payments/Refunds pages are operational ledgers that reuse billing APIs and link back to Billing.
