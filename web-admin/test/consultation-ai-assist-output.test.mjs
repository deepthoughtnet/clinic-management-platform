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
  assert.ok(source.includes("Consultation Snapshot"));
  assert.ok(source.includes("AI Clinical Summary"));
  assert.ok(source.includes("Context available to AIVA"));
  assert.ok(source.includes("Prompt shortcuts"));
  assert.ok(source.includes("Clinical reasoning"));
  assert.ok(source.includes("Investigations"));
  assert.ok(source.includes("Prescription and safety"));
  assert.ok(source.includes("Patient communication"));
  assert.ok(source.includes("Generate summary"));
  assert.ok(source.includes("Show Transcript"));
  assert.ok(source.includes("AIVA_QUICK_PROMPTS") || source.includes("AI_ASSIST_PROMPT_GROUPS"));
  assert.ok(!source.includes("AIVA Draft Review"));
  assert.ok(!source.includes("Accept All Pending"));
  assert.ok(!source.includes("Reject All Pending"));
  assert.ok(!source.includes("Clear Rejected"));
  assert.ok(!source.includes("clinicalDraftStats"));
  assert.ok(!source.includes("pendingAiDraftCount"));
  assert.ok(!source.includes("aivaDraftsExpanded"));
  assert.ok(cardSource.includes("No uploaded reports available."));
  assert.ok(cardSource.includes("No previous consultations available."));
});
