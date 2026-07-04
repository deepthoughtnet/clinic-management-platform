import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("patient intelligence card exposes compact longitudinal sections", () => {
  const source = readSource("components/clinical/PatientIntelligenceCard.tsx");
  assert.ok(source.includes("Patient Intelligence"));
  assert.ok(source.includes("Patient Snapshot"));
  assert.ok(source.includes("Clinical Highlights"));
  assert.ok(source.includes("View Details"));
  assert.ok(source.includes("Intake status"));
  assert.ok(source.includes("Intake"));
  assert.ok(source.includes("Latest vitals"));
  assert.ok(source.includes("Previous diagnosis"));
  assert.ok(source.includes("Medication alerts"));
  assert.ok(source.includes("Recent lab alerts"));
  assert.ok(source.includes("Recent reports"));
  assert.ok(source.includes("Timeline summary"));
  assert.ok(source.includes("Longitudinal patient context. Not AI generated."));
  assert.ok(source.includes("View more"));
  assert.ok(source.includes("No previous consultations available."));
  assert.ok(source.includes("No uploaded reports available."));
});
