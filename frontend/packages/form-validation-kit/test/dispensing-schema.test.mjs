import test from "node:test";
import assert from "node:assert/strict";

import {
  closureReasonSchema,
  createDispenseActionInputSchema,
  dispensingActionSchema,
  remarksSchema,
} from "../dist/index.js";

test("dispensing action schema accepts supported actions", () => {
  assert.equal(dispensingActionSchema.safeParse("FULL_DISPENSE").success, true);
  assert.equal(dispensingActionSchema.safeParse("PATIENT_DECLINED").success, true);
});

test("dispense action input requires quantity for partial dispense", () => {
  const schema = createDispenseActionInputSchema({ pendingQuantity: 4, availableQuantity: 2, action: "PARTIAL_DISPENSE" });
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: 0, batchOverride: null, reason: null, remarks: null }).success, false);
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: 3, batchOverride: null, reason: null, remarks: null }).success, false);
  assert.equal(schema.safeParse({ action: "PARTIAL_DISPENSE", quantity: 2, batchOverride: null, reason: null, remarks: null }).success, true);
});

test("closure reason and remarks validate length", () => {
  assert.equal(closureReasonSchema.safeParse("Stock unavailable").success, true);
  assert.equal(closureReasonSchema.safeParse("x".repeat(61)).success, false);
  assert.equal(remarksSchema.safeParse("x".repeat(250)).success, true);
  assert.equal(remarksSchema.safeParse("x".repeat(251)).success, false);
});
