import test from "node:test";
import assert from "node:assert/strict";

import {
  inventoryBatchCreateSchema,
  inventoryBatchBaseSchema,
  inventoryBatchEditSchema,
  inventoryCustomerReturnSchema,
  inventoryMovementSchema,
  inventoryTransactionFormSchema,
  inventoryVendorReturnSchema,
  inventoryWriteOffSchema,
  inventoryBatchKey,
} from "../dist/index.js";

const baseBatch = {
  medicineId: "11111111-1111-4111-8111-111111111111",
  locationId: "22222222-2222-4222-8222-222222222222",
  batchNumber: "PCM2401",
  expiryDate: new Date(Date.now() + 86_400_000).toISOString().slice(0, 10),
  quantityOnHand: 10,
  lowStockThreshold: 2,
  unitCost: 12.5,
  purchasePrice: 12.5,
  sellingPrice: 15,
  barcode: "8901234567890",
  qrCode: "QR-001",
  externalCode: "EXT-001",
  purchaseReferenceNumber: "GRN 1001",
  active: true,
};

test("inventory batch create schema accepts valid payload", () => {
  const result = inventoryBatchBaseSchema.safeParse(baseBatch);
  assert.equal(result.success, true);
  assert.equal(inventoryBatchCreateSchema.safeParse(baseBatch).success, true);
});

test("inventory batch create schema rejects blank or symbol-only batch number", () => {
  assert.equal(inventoryBatchBaseSchema.safeParse({ ...baseBatch, batchNumber: "   " }).success, false);
  assert.equal(inventoryBatchBaseSchema.safeParse({ ...baseBatch, batchNumber: "@@@###" }).success, false);
});

test("inventory batch create schema rejects quantity zero and mrp less than purchase rate", () => {
  assert.equal(inventoryBatchBaseSchema.safeParse({ ...baseBatch, quantityOnHand: 0 }).success, false);
  assert.equal(inventoryBatchBaseSchema.safeParse({ ...baseBatch, sellingPrice: 11.5 }).success, false);
});

test("inventory batch edit schema allows zero quantity but still validates edit payload", () => {
  const result = inventoryBatchEditSchema.safeParse({ ...baseBatch, quantityOnHand: 0 });
  assert.equal(result.success, true);
});

test("inventory batch create schema rejects zero quantity", () => {
  assert.equal(inventoryBatchCreateSchema.safeParse({ ...baseBatch, quantityOnHand: 0 }).success, false);
});

test("inventory key normalizes medicine location and batch", () => {
  assert.equal(
    inventoryBatchKey({ medicineId: " A ", locationId: " b ", batchNumber: " pcm2401 " }),
    "A|b|PCM2401",
  );
});

test("inventory return schema validates return quantity and condition", () => {
  assert.equal(
    inventoryCustomerReturnSchema.safeParse({
      saleId: "33333333-3333-4333-8333-333333333333",
      saleLineId: "44444444-4444-4444-8444-444444444444",
      returnQuantity: 1,
      condition: "REUSABLE",
      refundMode: "CASH",
      reason: "Patient request",
      referenceNumber: "REF-1",
      notes: "Reusable return",
    }).success,
    true,
  );
});

test("inventory vendor return and write off schemas validate required fields", () => {
  assert.equal(
    inventoryVendorReturnSchema.safeParse({
      medicineId: baseBatch.medicineId,
      stockBatchId: "55555555-5555-4555-8555-555555555555",
      returnQuantity: 2,
      supplierReference: "INV-1",
      reason: "Damaged stock",
      notes: "Return to supplier",
    }).success,
    true,
  );
  assert.equal(
    inventoryWriteOffSchema.safeParse({
      medicineId: baseBatch.medicineId,
      stockBatchId: "55555555-5555-4555-8555-555555555555",
      writeOffQuantity: 2,
      reason: "Expired stock",
      notes: "Written off",
    }).success,
    true,
  );
});

test("inventory movement schema validates stock movements", () => {
  assert.equal(
    inventoryMovementSchema.safeParse({
      medicineId: baseBatch.medicineId,
      stockBatchId: "55555555-5555-4555-8555-555555555555",
      locationId: baseBatch.locationId,
      movementType: "STOCK_ADJUSTMENT",
      beforeQuantity: 10,
      afterQuantity: 12,
      quantityDelta: 2,
      referenceType: "PHYSICAL_COUNT",
      referenceId: "66666666-6666-4666-8666-666666666666",
      reason: "Count adjustment",
      notes: "Delta recorded",
    }).success,
    true,
  );
});

test("inventory transaction form schema validates stock adjustment notes", () => {
  assert.equal(
    inventoryTransactionFormSchema.safeParse({
      medicineId: baseBatch.medicineId,
      stockBatchId: "55555555-5555-4555-8555-555555555555",
      transactionType: "ADJUSTMENT",
      quantity: 1,
      referenceType: "PHYSICAL_STOCK_COUNT",
      referenceId: "66666666-6666-4666-8666-666666666666",
      notes: "Counted variance",
    }).success,
    true,
  );
  assert.equal(
    inventoryTransactionFormSchema.safeParse({
      medicineId: baseBatch.medicineId,
      stockBatchId: "55555555-5555-4555-8555-555555555555",
      transactionType: "ADJUSTMENT",
      quantity: 1,
      referenceType: "PHYSICAL_STOCK_COUNT",
      referenceId: "66666666-6666-4666-8666-666666666666",
      notes: "",
    }).success,
    false,
  );
});
