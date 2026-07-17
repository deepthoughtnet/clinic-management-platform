import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("prescription workspace UI polish keeps medicine row ordered and aligned", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("alignItems=\"end\""));
  assert.ok(source.includes("label={`Medicine ${index + 1}`}"));
  assert.ok(source.includes('label="Strength"'));
  assert.ok(source.includes('label="Route"'));
  assert.ok(source.includes('label="Dosage"'));
  assert.ok(source.includes('label="Frequency"'));
  assert.ok(source.includes('label="Duration"'));
  assert.ok(source.includes('aria-label={`Delete medicine row ${index + 1}`}'));
  assert.ok(source.indexOf('label={`Medicine ${index + 1}`}') < source.indexOf('label="Strength"'));
  assert.ok(source.indexOf('label="Strength"') < source.indexOf('label="Route"'));
  assert.ok(source.indexOf('label="Route"') < source.indexOf('label="Dosage"'));
  assert.ok(source.indexOf('label="Dosage"') < source.indexOf('label="Frequency"'));
  assert.ok(source.indexOf('label="Frequency"') < source.indexOf('label="Duration"'));
});

test("prescription workspace UI polish exposes diagnosis chips, advice helper text, and follow-up shortcuts", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("prescriptionDiagnosisSnapshotEntries"));
  assert.ok(source.includes('aria-label="Diagnosis snapshot chips"'));
  assert.ok(source.includes("No diagnosis recorded."));
  assert.ok(source.includes("Clinical advice carried from consultation. Edit before finalizing if needed."));
  assert.ok(source.includes("multiline"));
  assert.ok(source.includes("minRows={3}"));
  assert.ok(source.includes("PRESCRIPTION_FOLLOWUP_SHORTCUTS"));
  assert.ok(source.includes("applyPrescriptionFollowUpShortcut"));
  assert.ok(source.includes("focusPrescriptionFollowUpInput"));
  assert.ok(source.includes('aria-label="Follow-up shortcuts"'));
  assert.ok(source.includes('label="Custom"'));
});

test("prescription workspace UI polish keeps checklist and suggestion density compact", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes('prescriptionQualityChecklist.filter((item) => item.state === "complete")'));
  assert.ok(source.includes('prescriptionQualityChecklist.filter((item) => item.state !== "complete")'));
  assert.ok(source.includes('Stack spacing={0.35}'));
  assert.ok(source.includes('Reason: {item.reason}'));
  assert.ok(source.includes('Safety: {item.safetyNote}'));
});
