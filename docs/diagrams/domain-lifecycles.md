# Domain Lifecycles

## Webinar
```mermaid
stateDiagram-v2
  [*] --> DRAFT
  DRAFT --> SCHEDULED
  SCHEDULED --> LIVE
  LIVE --> COMPLETED
  DRAFT --> CANCELLED
  SCHEDULED --> CANCELLED
```

## Billing/Payment/Refund
```mermaid
flowchart LR
  BillCreated --> Issued
  Issued --> PaymentPosted
  PaymentPosted --> FullyPaid
  PaymentPosted --> PartialPaid
  FullyPaid --> RefundIssued
  PartialPaid --> RefundIssued
```

## Pharmacy Dispensing
```mermaid
flowchart LR
  PrescriptionFinalized --> DispenseQueue
  DispenseQueue --> FEFOSelect[Batch Selection]
  FEFOSelect --> StockDeduct
  StockDeduct --> DispenseRecorded
  DispenseRecorded --> OptionalBill
```
