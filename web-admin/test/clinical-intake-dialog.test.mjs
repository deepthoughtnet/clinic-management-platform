import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("clinical intake dialog captures vitals and reuses document upload flow", () => {
  const source = readSource("components/clinical/ClinicalIntakeDialog.tsx");
  assert.ok(source.includes("Clinical Intake -"));
  assert.ok(source.includes("Optional chief complaint"));
  assert.ok(source.includes("Height (cm)"));
  assert.ok(source.includes("Weight (kg)"));
  assert.ok(source.includes("BP Systolic"));
  assert.ok(source.includes("BP Diastolic"));
  assert.ok(source.includes("Pulse"));
  assert.ok(source.includes("Temperature"));
  assert.ok(source.includes("SpO2"));
  assert.ok(source.includes("Random blood sugar"));
  assert.ok(source.includes("Mark intake complete"));
  assert.ok(source.includes("Upload report / referral"));
  assert.ok(source.includes("Save Intake"));
  assert.ok(source.includes("clinic:clinical-intake-updated"));
});
