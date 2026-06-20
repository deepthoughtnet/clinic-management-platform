import test from "node:test";
import assert from "node:assert/strict";

import {
  closureReasonSchema,
  createDispenseActionInputSchema,
  dispensingActionSchema,
  dispensingQueueSearchSchema,
  remarksSchema,
} from "../dist/index.js";

test("dispensing action schema accepts supported actions", () => {
  assert.equal(dispensingActionSchema.safeParse("FULL_DISPENSE").success, true);
  assert.equal(dispensingActionSchema.safeParse("PATIENT_DECLINED").success, true);
});

test("dispense action input requires quantity for partial dispense", () => {
  const schema = createDispenseActionInputSchema({ pendingQuantity: 4, availableQuantity: 2, action: "PARTIAL_DISPENSE" });
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: 0, batchOverride: null, reason: null, remarks: null }).success, false);
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: -1, batchOverride: null, reason: null, remarks: null }).success, false);
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: 1.5, batchOverride: null, reason: null, remarks: null }).success, false);
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: 3, batchOverride: null, reason: null, remarks: null }).success, false);
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: 2, batchOverride: null, reason: null, remarks: null }).success, true);
});

test("full dispense blocks when stock is unavailable", () => {
  const schema = createDispenseActionInputSchema({ pendingQuantity: 2, availableQuantity: 0, action: "FULL_DISPENSE" });
  assert.equal(schema.safeParse({ action: "FULL_DISPENSE", quantity: 1, batchOverride: null, reason: null, remarks: null }).success, false);
});

test("closure reason and remarks validate length", () => {
  assert.equal(closureReasonSchema.safeParse("Stock unavailable").success, true);
  assert.equal(closureReasonSchema.safeParse("x".repeat(61)).success, false);
  assert.equal(remarksSchema.safeParse("x".repeat(250)).success, true);
  assert.equal(remarksSchema.safeParse("x".repeat(251)).success, false);
});

test("dispensing queue search trims and enforces max length", () => {
  assert.equal(dispensingQueueSearchSchema.safeParse("  paracetamol  ").success, true);
  const parsed = dispensingQueueSearchSchema.safeParse("  prescription  ");
  assert.equal(parsed.success, true);
  if (parsed.success) {
    assert.equal(parsed.data, "prescription");
  }
  assert.equal(dispensingQueueSearchSchema.safeParse("x".repeat(61)).success, false);
});
