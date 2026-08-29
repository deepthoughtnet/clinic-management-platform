import test from "node:test";
import assert from "node:assert/strict";

import { hasDuplicateMedicineMaster, medicineMasterIdentityKey, medicineMasterSchema, medicineTimingSchema, pharmacyPosLineSchema, pharmacyPosSaleSchema, stockInwardSchema } from "../dist/index.js";

function offsetUtcDate(days) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

const baseMedicine = {
  medicineName: "Paracetamol 500",
  medicineType: "TABLET",
  barcode: "PARA-500_A",
  qrCode: "QR-500",
  externalCode: "EXT-500",
  genericName: "Paracetamol",
  brandName: "Dolo 500",
  category: "Analgesic",
  dosageForm: "Tablet",
  strength: "500 mg",
  unit: "Tablet",
  manufacturer: "ACME Pharma",
  defaultDosage: "1 tablet",
  defaultFrequency: "Twice daily",
  defaultDurationDays: 5,
  defaultTiming: "AFTER_FOOD",
  defaultInstructions: "Take after food",
  defaultPrice: 12.5,
  taxRate: 12,
  active: true,
};

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

test("medicine master schema accepts a valid medicine", () => {
  const result = medicineMasterSchema.safeParse(baseMedicine);
  assert.equal(result.success, true);
});

test("medicine master schema rejects blank medicine name", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, medicineName: "" }).success, false);
});

test("medicine master schema rejects spaces-only medicine name", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, medicineName: "   " }).success, false);
});

test("medicine master schema rejects symbol-only medicine name", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, medicineName: "@@@###" }).success, false);
});

test("medicine master schema rejects medicine name longer than 60 characters", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, medicineName: "a".repeat(61) }).success, false);
});

test("medicine master schema rejects invalid medicine types", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, medicineType: "PILL" }).success, false);
});

test("medicine master schema accepts sachet medicines", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, medicineType: "SACHET" }).success, true);
});

test("medicine master schema rejects invalid barcode formats", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, barcode: "BAD CODE" }).success, false);
});

test("medicine master schema rejects duplicate medicine identity", () => {
  assert.equal(
    hasDuplicateMedicineMaster(
      { medicineName: "Paracetamol", medicineType: "TABLET", strength: "500 mg" },
      [
        { id: "1", medicineName: " Paracetamol ", medicineType: "TABLET", strength: "500 mg" },
        { id: "2", medicineName: "Ibuprofen", medicineType: "TABLET", strength: "200 mg" },
      ],
    ),
    true,
  );
});

test("medicine master schema rejects blank strength", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, strength: "" }).success, false);
});

test("medicine master schema rejects symbol-only strength", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, strength: "@@@###" }).success, false);
});

test("medicine master schema rejects negative price", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, defaultPrice: -1 }).success, false);
});

test("medicine master schema allows zero price", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, defaultPrice: 0 }).success, true);
});

test("medicine master schema rejects price with more than two decimals", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, defaultPrice: 12.345 }).success, false);
});

test("medicine master schema rejects invalid tax percentages", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, taxRate: -1 }).success, false);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, taxRate: 101 }).success, false);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, taxRate: 999 }).success, false);
});

test("medicine master schema allows boundary tax percentages", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, taxRate: 0 }).success, true);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, taxRate: 12 }).success, true);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, taxRate: 100 }).success, true);
});

test("medicine master schema rejects tax with more than two decimals", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, taxRate: 12.345 }).success, false);
});

test("medicine master schema rejects text fields longer than 60 characters", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, genericName: "g".repeat(61) }).success, false);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, brandName: "b".repeat(61) }).success, false);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, category: "c".repeat(61) }).success, false);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, dosageForm: "f".repeat(61) }).success, false);
});

test("medicine master schema rejects symbol-only generic and brand names", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, genericName: "@@@###" }).success, false);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, brandName: "@@@###" }).success, false);
});

test("medicine master schema allows instructions up to 250 characters", () => {
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, defaultInstructions: "i".repeat(250) }).success, true);
  assert.equal(medicineMasterSchema.safeParse({ ...baseMedicine, defaultInstructions: "i".repeat(251) }).success, false);
});

test("medicine timing schema accepts canonical codes and rejects free text", () => {
  assert.equal(medicineTimingSchema.safeParse("BEFORE_FOOD").success, true);
  assert.equal(medicineTimingSchema.safeParse("AFTER_FOOD").success, true);
  assert.equal(medicineTimingSchema.safeParse("WITH_FOOD").success, true);
  assert.equal(medicineTimingSchema.safeParse("ANYTIME").success, true);
  assert.equal(medicineTimingSchema.safeParse("After food").success, false);
});

test("stock inward schema accepts a selected location and future expiry date", () => {
  const result = stockInwardSchema.safeParse({
    medicineId: "11111111-1111-4111-8111-111111111111",
    supplierId: null,
    locationId: "22222222-2222-4222-8222-222222222222",
    purchaseReferenceNumber: "DGR-UAT-0001",
    batchNumber: "PAN-UAT-D01",
    barcode: null,
    qrCode: null,
    externalCode: null,
    expiryDate: offsetUtcDate(30),
    purchaseDate: offsetUtcDate(0),
    quantity: 50,
    lowStockThreshold: 15,
    unitCost: 6,
    sellingPrice: 8,
  });
  assert.equal(result.success, true);
});

test("stock inward schema rejects missing location", () => {
  const result = stockInwardSchema.safeParse({
    medicineId: "11111111-1111-4111-8111-111111111111",
    supplierId: null,
    locationId: "",
    purchaseReferenceNumber: "DGR-UAT-0001",
    batchNumber: "PAN-UAT-D01",
    barcode: null,
    qrCode: null,
    externalCode: null,
    expiryDate: offsetUtcDate(30),
    purchaseDate: offsetUtcDate(0),
    quantity: 50,
    lowStockThreshold: 15,
    unitCost: 6,
    sellingPrice: 8,
  });
  assert.equal(result.success, false);
});

test("stock inward schema rejects future received dates and accepts future expiry dates", () => {
  const receivedInFuture = stockInwardSchema.safeParse({
    medicineId: "11111111-1111-4111-8111-111111111111",
    supplierId: null,
    locationId: "22222222-2222-4222-8222-222222222222",
    purchaseReferenceNumber: "DGR-UAT-0001",
    batchNumber: "PAN-UAT-D01",
    barcode: null,
    qrCode: null,
    externalCode: null,
    expiryDate: offsetUtcDate(30),
    purchaseDate: offsetUtcDate(1),
    quantity: 50,
    lowStockThreshold: 15,
    unitCost: 6,
    sellingPrice: 8,
  });
  assert.equal(receivedInFuture.success, false);

  const expiryFuture = stockInwardSchema.safeParse({
    medicineId: "11111111-1111-4111-8111-111111111111",
    supplierId: null,
    locationId: "22222222-2222-4222-8222-222222222222",
    purchaseReferenceNumber: "DGR-UAT-0001",
    batchNumber: "PAN-UAT-D01",
    barcode: null,
    qrCode: null,
    externalCode: null,
    expiryDate: offsetUtcDate(30),
    purchaseDate: offsetUtcDate(0),
    quantity: 50,
    lowStockThreshold: 15,
    unitCost: 6,
    sellingPrice: 8,
  });
  assert.equal(expiryFuture.success, true);
});

test("medicine master identity key normalizes name, strength, and type", () => {
  assert.equal(
    medicineMasterIdentityKey({
      medicineName: " Paracetamol ",
      medicineType: "TABLET",
      strength: " 500 mg ",
    }),
    "TABLET|paracetamol|500 mg",
  );
});
