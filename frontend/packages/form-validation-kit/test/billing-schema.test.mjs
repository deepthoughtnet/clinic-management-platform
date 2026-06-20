import test from "node:test";
import assert from "node:assert/strict";

import {
  billingBillDraftSchema,
  billingCreatePaymentSchema,
  billingLedgerFilterSchema,
  billingRefundSchema,
  consultationFeeSchema,
  invoiceSchema,
  paymentSchema,
} from "../dist/index.js";

test("billing payment schema accepts a valid payload", () => {
  const result = paymentSchema.safeParse({
    amount: 150,
    paymentMethod: "CASH",
    invoiceNumber: "",
    notes: "Collected at desk",
  });
  assert.equal(result.success, true);
});

test("billing payment schema rejects a missing payment method", () => {
  const result = paymentSchema.safeParse({
    amount: 150,
    paymentMethod: "",
  });
  assert.equal(result.success, false);
});

test("billing consultation fee schema rejects a non-positive amount", () => {
  const result = consultationFeeSchema.safeParse({
    amount: 0,
    paymentMethod: "UPI",
  });
  assert.equal(result.success, false);
});

test("billing invoice schema allows optional invoice number and notes", () => {
  const result = invoiceSchema.safeParse({
    amount: 500,
    paymentMethod: "CARD",
  });
  assert.equal(result.success, true);
});

test("billing draft schema rejects missing patient and future dates", () => {
  const result = billingBillDraftSchema.safeParse({
    patientId: "",
    billDate: "2999-01-01",
    source: "MANUAL_BILLING",
    discountType: "NONE",
    discountValue: "",
    discountReason: "",
    consultationId: "",
    appointmentId: "",
    notes: "",
    lines: [],
  });
  assert.equal(result.success, false);
});

test("billing payment schema rejects text amounts and missing references for non-cash payments", () => {
  const result = billingCreatePaymentSchema.safeParse({
    paymentAmount: "text",
    paymentMode: "UPI",
    referenceNumber: "",
    notes: "",
  });
  assert.equal(result.success, false);
});

test("billing refund schema requires a reason", () => {
  const result = billingRefundSchema.safeParse({
    amount: 50,
    refundMode: "CASH",
    reason: "",
    notes: "",
  });
  assert.equal(result.success, false);
});

test("billing ledger filters enforce date order", () => {
  const result = billingLedgerFilterSchema.safeParse({
    search: "bill",
    status: "PAID",
    fromDate: "2026-06-20",
    toDate: "2026-06-01",
    paymentMode: "CASH",
  });
  assert.equal(result.success, false);
});
