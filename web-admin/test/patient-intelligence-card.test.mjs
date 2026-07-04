import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient intelligence card exposes compact longitudinal sections", () => {
  const source = readSource("components/clinical/PatientIntelligenceCard.tsx");
  assert.ok(source.includes("Patient Intelligence"));
  assert.ok(source.includes("Patient Snapshot"));
  assert.ok(source.includes("Previous Diagnosis"));
  assert.ok(source.includes("Medication Alerts"));
  assert.ok(source.includes("Intake"));
  assert.ok(source.includes("Intake complete"));
  assert.ok(source.includes("Latest vitals"));
  assert.ok(source.includes("Chief complaint"));
  assert.ok(source.includes("Recent Lab Alerts"));
  assert.ok(source.includes("Recent Uploaded Reports"));
  assert.ok(source.includes("Timeline Summary"));
  assert.ok(source.includes("Longitudinal patient context. Not AI generated."));
});
