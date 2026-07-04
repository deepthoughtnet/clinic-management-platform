import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation workspace freeze keeps AI surfaces compact and reviewable", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const cardSource = readSource("components/clinical/PatientIntelligenceCard.tsx");

  assert.ok(source.includes("Review Context → Ask AI → Review Draft → Accept"));
  assert.ok(source.includes("Clinical Chat"));
  assert.ok(source.includes("Consultation Checklist"));
  assert.ok(source.includes("Consultation Progress"));
  assert.ok(source.includes("Ready to complete"));
  assert.ok(source.includes("Review reasoning"));
  assert.ok(source.includes("Generate SOAP using AIVA or complete manually."));
  assert.ok(source.includes("Generate Consultation Draft"));
  assert.ok(source.includes("AIVA Draft Review"));
  assert.ok(source.includes("Show less diagnosis chips"));
  assert.ok(source.includes("more diagnosis chips"));
  assert.ok(cardSource.includes("No uploaded reports available."));
  assert.ok(cardSource.includes("No previous consultations available."));
});
