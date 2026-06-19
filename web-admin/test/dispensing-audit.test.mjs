import test from "node:test";
import assert from "node:assert/strict";

import {
  appendDispensingAuditEntry,
  getPrescriptionDispensingState,
  isTerminalDispensingState,
  setPrescriptionDispensingState,
  shouldHideFromActiveQueue,
} from "../src/shared/components/comment-suggestions/dispensingAuditStore.js";

function createMemoryStorage() {
  const store = new Map();
  return {
    getItem(key) {
      return store.has(key) ? store.get(key) : null;
    },
    setItem(key, value) {
      store.set(key, value);
    },
  };
}

test("dispensing audit records selected reason and final remarks", () => {
  const storage = createMemoryStorage();
  appendDispensingAuditEntry(storage, {
    prescriptionId: "p1",
    medicineLineId: "m1",
    action: "PARTIAL_DISPENSE",
    previousStatus: "NOT_DISPENSED",
    newStatus: "PARTIALLY_DISPENSED",
    quantity: 1,
    batch: "B1",
    reason: "PATIENT_PREFERENCE",
    remarks: "Patient will return later.",
    user: "tester",
    timestamp: "2026-06-19T00:00:00.000Z",
  });
  assert.equal(getPrescriptionDispensingState(storage, "p1"), null);
  setPrescriptionDispensingState(storage, "p1", { status: "PARTIALLY_DISPENSED", lineStates: { m1: "PARTIALLY_DISPENSED" } });
  assert.equal(shouldHideFromActiveQueue(storage, "p1", "NOT_DISPENSED"), false);
  setPrescriptionDispensingState(storage, "p1", { status: "BOUGHT_EXTERNALLY", lineStates: { m1: "BOUGHT_EXTERNALLY" } });
  assert.equal(isTerminalDispensingState("BOUGHT_EXTERNALLY"), true);
  assert.equal(shouldHideFromActiveQueue(storage, "p1", "NOT_DISPENSED"), true);
});
