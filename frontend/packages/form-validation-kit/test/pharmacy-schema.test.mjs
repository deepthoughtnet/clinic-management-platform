import test from "node:test";
import assert from "node:assert/strict";

import { pharmacyPosLineSchema, pharmacyPosSaleSchema } from "../dist/index.js";

test("pharmacy line schema accepts a valid payload", () => {
  const result = pharmacyPosLineSchema.safeParse({
    medicine: "Paracetamol",
    quantity: 2,
    unitPrice: 10,
    discount: 0,
  });
  assert.equal(result.success, true);
});

test("pharmacy line schema rejects a missing medicine", () => {
  const result = pharmacyPosLineSchema.safeParse({
    medicine: "",
    quantity: 2,
    unitPrice: 10,
    discount: 0,
  });
  assert.equal(result.success, false);
});

test("pharmacy line schema rejects non-positive quantity", () => {
  const result = pharmacyPosLineSchema.safeParse({
    medicine: "Paracetamol",
    quantity: 0,
    unitPrice: 10,
    discount: 0,
  });
  assert.equal(result.success, false);
});

test("pharmacy sale schema allows one valid line", () => {
  const result = pharmacyPosSaleSchema.safeParse({
    items: [
      {
        medicine: "Paracetamol",
        quantity: 2,
        unitPrice: 10,
        discount: 0,
      },
    ],
  });
  assert.equal(result.success, true);
});
