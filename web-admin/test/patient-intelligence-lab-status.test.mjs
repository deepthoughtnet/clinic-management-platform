import test from "node:test";
import assert from "node:assert/strict";
import { resolveTrustedLabStatus } from "../src/components/clinical/patientIntelligenceLabStatus.js";

test("trusted lab status preserves authoritative interpretation badges", () => {
  assert.deepEqual(resolveTrustedLabStatus({ interpretation: "HIGH" }), { label: "HIGH", tone: "error" });
  assert.deepEqual(resolveTrustedLabStatus({ interpretation: "High" }), { label: "HIGH", tone: "error" });
  assert.deepEqual(resolveTrustedLabStatus({ interpretation: "LOW" }), { label: "LOW", tone: "warning" });
  assert.deepEqual(resolveTrustedLabStatus({ interpretation: "Normal" }), { label: "NORMAL", tone: "success" });
});

test("trusted lab status falls back to Unknown when no interpretation is available", () => {
  assert.deepEqual(resolveTrustedLabStatus({ interpretation: null }), { label: "UNKNOWN", tone: "default" });
  assert.deepEqual(resolveTrustedLabStatus({}), { label: "UNKNOWN", tone: "default" });
});
