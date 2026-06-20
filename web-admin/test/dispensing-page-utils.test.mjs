import test from "node:test";
import assert from "node:assert/strict";

import { isActiveDispenseStatus, isTerminalDispenseStatus, matchesDispensingSearch, queueRowMatchesFilter } from "../src/pages/pharmacy/dispensingPageUtils.js";

test("terminal dispensing statuses are excluded from active queue", () => {
  assert.equal(isActiveDispenseStatus("READY_FOR_DISPENSE"), true);
  assert.equal(isActiveDispenseStatus("PARTIALLY_DISPENSED"), true);
  assert.equal(isTerminalDispenseStatus("FULLY_DISPENSED"), true);
  assert.equal(isTerminalDispenseStatus("BOUGHT_EXTERNALLY"), true);
});

test("queue filter matches the expected status groups", () => {
  assert.equal(queueRowMatchesFilter("NOT_DISPENSED", "ACTIVE"), true);
  assert.equal(queueRowMatchesFilter("FULLY_DISPENSED", "ACTIVE"), false);
  assert.equal(queueRowMatchesFilter("FULLY_DISPENSED", "FULLY_DISPENSED"), true);
});

test("dispensing search checks patient doctor prescription and medicines", () => {
  const row = {
    prescriptionNumber: "RX-100",
    patientName: "Asha Patel",
    doctorName: "Dr. Rao",
    lines: [{ prescribedMedicineName: "Amoxicillin" }, { prescribedMedicineName: "Paracetamol" }],
  };
  assert.equal(matchesDispensingSearch(row, "  paracetamol "), true);
  assert.equal(matchesDispensingSearch(row, "  RX-100 "), true);
  assert.equal(matchesDispensingSearch(row, "unknown"), false);
});
