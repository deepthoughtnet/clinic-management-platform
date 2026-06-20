import test from "node:test";
import assert from "node:assert/strict";

import {
  pharmacyPosCartLineSchema,
  pharmacyPosCheckoutSchema,
  pharmacyPosSearchSchema,
} from "../dist/index.js";

test("pharmacy pos search schema trims and accepts valid text", () => {
  const result = pharmacyPosSearchSchema.safeParse("  batch  ");
  assert.equal(result.success, true);
  if (result.success) {
    assert.equal(result.data, "batch");
  }
});

test("pharmacy pos search schema rejects long text", () => {
  assert.equal(pharmacyPosSearchSchema.safeParse("a".repeat(61)).success, false);
});

test("pharmacy pos cart line schema accepts a valid line", () => {
  const result = pharmacyPosCartLineSchema.safeParse({
    medicineId: "11111111-1111-4111-8111-111111111111",
    quantity: 2,
    availableQuantity: 5,
    unitPrice: 10,
    discount: 0,
    taxRate: 5,
  });
  assert.equal(result.success, true);
});

test("pharmacy pos cart line schema rejects quantity above stock", () => {
  assert.equal(
    pharmacyPosCartLineSchema.safeParse({
      medicineId: "11111111-1111-4111-8111-111111111111",
      quantity: 6,
      availableQuantity: 5,
      unitPrice: 10,
      discount: 0,
      taxRate: 5,
    }).success,
    false,
  );
});

test("pharmacy pos cart line schema rejects discount above gross amount", () => {
  assert.equal(
    pharmacyPosCartLineSchema.safeParse({
      medicineId: "11111111-1111-4111-8111-111111111111",
      quantity: 1,
      availableQuantity: 5,
      unitPrice: 10,
      discount: 11,
      taxRate: 0,
    }).success,
    false,
  );
});

test("pharmacy pos checkout schema accepts valid payment data", () => {
  const result = pharmacyPosCheckoutSchema.safeParse({
    items: [
      {
        medicineId: "11111111-1111-4111-8111-111111111111",
        quantity: 2,
        availableQuantity: 5,
        unitPrice: 10,
        discount: 0,
        taxRate: 5,
      },
    ],
    patientId: null,
    customerName: null,
    customerMobile: null,
    grandTotal: 21,
    paidAmount: 21,
    paymentMode: "CASH",
    paymentReference: null,
    prescriptionDocumentId: null,
    notes: null,
  });
  assert.equal(result.success, true);
});

test("pharmacy pos checkout schema rejects insufficient payment", () => {
  const result = pharmacyPosCheckoutSchema.safeParse({
    items: [
      {
        medicineId: "11111111-1111-4111-8111-111111111111",
        quantity: 2,
        availableQuantity: 5,
        unitPrice: 10,
        discount: 0,
        taxRate: 5,
      },
    ],
    patientId: null,
    customerName: null,
    customerMobile: null,
    grandTotal: 21,
    paidAmount: 10,
    paymentMode: "CASH",
    paymentReference: null,
    prescriptionDocumentId: null,
    notes: null,
  });
  assert.equal(result.success, false);
});

test("pharmacy pos checkout schema requires reference for non-cash payment", () => {
  const result = pharmacyPosCheckoutSchema.safeParse({
    items: [
      {
        medicineId: "11111111-1111-4111-8111-111111111111",
        quantity: 1,
        availableQuantity: 5,
        unitPrice: 10,
        discount: 0,
        taxRate: 0,
      },
    ],
    patientId: null,
    customerName: null,
    customerMobile: null,
    grandTotal: 10,
    paidAmount: 10,
    paymentMode: "UPI",
    paymentReference: "",
    prescriptionDocumentId: null,
    notes: null,
  });
  assert.equal(result.success, false);
});

test("pharmacy pos checkout schema rejects symbol-only walk-in names", () => {
  const result = pharmacyPosCheckoutSchema.safeParse({
    items: [
      {
        medicineId: "11111111-1111-4111-8111-111111111111",
        quantity: 1,
        availableQuantity: 5,
        unitPrice: 10,
        discount: 0,
        taxRate: 0,
      },
    ],
    patientId: null,
    customerName: "@@@###",
    customerMobile: null,
    grandTotal: 10,
    paidAmount: 10,
    paymentMode: "CASH",
    paymentReference: null,
    prescriptionDocumentId: null,
    notes: null,
  });
  assert.equal(result.success, false);
});

test("pharmacy pos checkout schema rejects invalid mobile numbers", () => {
  const result = pharmacyPosCheckoutSchema.safeParse({
    items: [
      {
        medicineId: "11111111-1111-4111-8111-111111111111",
        quantity: 1,
        availableQuantity: 5,
        unitPrice: 10,
        discount: 0,
        taxRate: 0,
      },
    ],
    patientId: null,
    customerName: "Walk-in",
    customerMobile: "12345",
    grandTotal: 10,
    paidAmount: 10,
    paymentMode: "CASH",
    paymentReference: null,
    prescriptionDocumentId: null,
    notes: null,
  });
  assert.equal(result.success, false);
});
