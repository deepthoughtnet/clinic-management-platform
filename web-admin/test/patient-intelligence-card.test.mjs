import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("patient intelligence card exposes grouped longitudinal sections", () => {
  const source = readSource("components/clinical/PatientIntelligenceCard.tsx");
  assert.ok(source.includes("Patient Intelligence"));
  assert.ok(source.includes("Patient Snapshot"));
  assert.ok(source.includes("patientSnapshotFallback"));
  assert.ok(source.includes("Clinical Highlights"));
  assert.ok(source.includes("aiEnabled = true"));
  assert.ok(source.includes("AI Extracted Summary"));
  assert.ok(source.includes("Conditions"));
  assert.ok(source.includes("Latest Labs"));
  assert.ok(source.includes("Risk Flags"));
  assert.ok(source.includes("Document"));
  assert.ok(source.includes("Review"));
  assert.ok(source.includes("View Source"));
  assert.ok(source.includes("aiEnabled ? ("));
  assert.ok(source.includes("SectionBox title=\"Lab intelligence\""));
  assert.ok(source.includes("Doctor verification required before becoming permanent patient history."));
  assert.ok(source.includes("Longitudinal patient context. Not AI generated."));
  assert.ok(source.includes("Patient intelligence becomes available after clinical documents or investigation results are added. AI-assisted summaries are currently unavailable."));
  assert.ok(source.includes("structuredLabTrends"));
  assert.ok(source.includes("longitudinalClinicalContext?.labTrends"));
  assert.ok(source.includes("StructuredTrendLine"));
  assert.ok(source.includes("sourceDocumentIds"));
  assert.ok(source.includes("clinicalInterpretation"));
  assert.ok(source.includes("previousTrends"));
  assert.ok(source.includes("join(\"-\")"));
  assert.ok(source.includes("Very High"));
  assert.ok(source.includes("High"));
  assert.ok(source.includes("Medium"));
  assert.ok(source.includes("Low"));
});
