import test from "node:test";
import assert from "node:assert/strict";

import { consultationFeeSchema, invoiceSchema, paymentSchema } from "../dist/index.js";

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
