import test from "node:test";
import assert from "node:assert/strict";
import { buildLatestTrustedLabProjection } from "../src/components/clinical/patientIntelligenceLatestLabs.js";

function lab(overrides = {}) {
  return {
    conceptFamily: "LAB_RESULT",
    conceptKey: "hemoglobin",
    label: "Hemoglobin",
    valueText: "12.7",
  valueUnit: "g/dL",
  sourceDocumentTitle: "CBC 2025",
  sourceDocumentType: "EXTERNAL_LAB_REPORT",
  sourceDocumentId: "doc-1",
  observedOn: "2025-08-22",
  confidence: 0.95,
  verificationStatus: "ACCEPTED",
  evidenceText: "Hemoglobin 12.7 g/dL",
  interpretation: "NORMAL",
  ...overrides,
  };
}

test("latest labs selects the most recent trusted observation per analyte", () => {
  const latest = buildLatestTrustedLabProjection([
    lab({ conceptKey: "hemoglobin", label: "Hemoglobin", valueText: "12.7", observedOn: "2025-08-22", sourceDocumentId: "doc-older" }),
    lab({ conceptKey: "hemoglobin", label: "Hemoglobin", valueText: "13.6", observedOn: "2026-08-23", sourceDocumentId: "doc-newer" }),
  ]);

  assert.equal(latest.length, 1);
  assert.equal(latest[0].conceptKey, "hemoglobin");
  assert.equal(latest[0].valueText, "13.6");
  assert.equal(latest[0].observedOn, "2026-08-23");
  assert.equal(latest[0].interpretation, "NORMAL");
});

test("latest labs keeps older history for trends but does not let it replace newer clinical observations", () => {
  const latest = buildLatestTrustedLabProjection([
    lab({ conceptKey: "hemoglobin", label: "Hemoglobin", valueText: "13.6", observedOn: "2026-08-23", sourceDocumentId: "doc-newer" }),
    lab({ conceptKey: "hemoglobin", label: "Hemoglobin", valueText: "12.7", observedOn: "2025-08-22", sourceDocumentId: "doc-older" }),
  ]);

  assert.equal(latest.length, 1);
  assert.equal(latest[0].valueText, "13.6");
});

test("latest labs excludes pending and rejected findings", () => {
  const latest = buildLatestTrustedLabProjection([
    lab({ conceptKey: "hemoglobin", valueText: "13.6", observedOn: "2026-08-23" }),
    lab({ conceptKey: "wbc", label: "WBC", valueText: "11.8", observedOn: "2026-08-23" }),
    lab({ conceptKey: "platelets", label: "Platelets", valueText: "271", observedOn: "2026-08-23", verificationStatus: "PENDING_REVIEW" }),
    lab({ conceptKey: "crp", label: "CRP", valueText: "12.4", observedOn: "2026-08-23", verificationStatus: "REJECTED" }),
  ]);

  assert.deepEqual(latest.map((item) => item.conceptKey).sort(), ["hemoglobin", "wbc"]);
});

test("latest labs preserves edited findings using the doctor-final value", () => {
  const latest = buildLatestTrustedLabProjection([
    lab({ conceptKey: "blood_sugar", label: "Blood Sugar", valueText: "105", valueUnit: "mg/dL", observedOn: "2026-02-14", sourceDocumentId: "doc-1", verificationStatus: "ACCEPTED" }),
  ]);

  assert.equal(latest.length, 1);
  assert.equal(latest[0].conceptKey, "blood_sugar");
  assert.equal(latest[0].valueText, "105");
  assert.equal(latest[0].valueUnit, "mg/dL");
});

test("latest labs resolves multiple analytes independently", () => {
  const latest = buildLatestTrustedLabProjection([
    lab({ conceptKey: "hemoglobin", label: "Hemoglobin", valueText: "13.6", observedOn: "2026-08-23", sourceDocumentId: "doc-1" }),
    lab({ conceptKey: "wbc", label: "WBC", valueText: "11.8", observedOn: "2026-08-23", sourceDocumentId: "doc-1" }),
    lab({ conceptKey: "platelets", label: "Platelets", valueText: "271", observedOn: "2026-08-23", sourceDocumentId: "doc-1" }),
    lab({ conceptKey: "crp", label: "CRP", valueText: "12.4", observedOn: "2026-08-23", sourceDocumentId: "doc-1" }),
  ]);

  assert.deepEqual(latest.map((item) => item.conceptKey).sort(), ["crp", "hemoglobin", "platelets", "wbc"]);
});
