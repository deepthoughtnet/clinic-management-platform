import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("consultation workspace renders AIVA chat and loading states", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("AIVA Chat"));
  assert.ok(source.includes("Doctor"));
  assert.ok(source.includes("AIVA"));
  assert.ok(source.includes("Ask AIVA anything about this consultation"));
  assert.ok(source.includes("Add to SOAP"));
  assert.ok(source.includes("Add to Advice"));
  assert.ok(source.includes("aiAssistActionLabel(aiActiveAction)"));
});

test("consultation workspace exposes an explicit clinical reasoning generate action", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("Generate clinical reasoning"));
  assert.ok(source.includes("Generating reasoning..."));
  assert.ok(source.includes("const canGenerateClinicalReasoning = Boolean"));
  assert.ok(source.includes("consultationForm.chiefComplaints.trim()"));
  assert.ok(source.includes("consultationForm.symptoms.trim()"));
  assert.ok(source.includes("consultationForm.diagnosis.trim()"));
  assert.ok(source.includes("consultationForm.clinicalNotes.trim()"));
  assert.ok(source.includes("disabled={!canGenerateClinicalReasoning}"));
  assert.ok(source.includes("disabled={readOnly || !aiDiagnosisSuggestion}"));
});

test("consultation workspace includes the phase 1 AI draft review center and field actions", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("Generate Consultation Draft"));
  assert.ok(source.includes("AIVA Draft Review"));
  assert.ok(source.includes("AIVA Drafts:"));
  assert.ok(source.includes("Accept All Pending"));
  assert.ok(source.includes("Reject All Pending"));
  assert.ok(source.includes("Clear Rejected"));
  assert.ok(source.includes("Generation progress"));
  assert.ok(source.includes("Consultation readiness:"));
  assert.ok(source.includes("Regenerate All"));
  assert.ok(source.includes("Draft"));
  assert.ok(source.includes("Extract"));
  assert.ok(source.includes("Suggest"));
  assert.ok(source.includes("Draft SOAP"));
  assert.ok(source.includes("Draft Advice"));
  assert.ok(source.includes("Collapse"));
  assert.ok(source.includes("Replace existing content or append?"));
  assert.ok(source.includes("runClinicalDraftAction(\"chiefComplaint\")"));
  assert.ok(source.includes("renderClinicalAiDraftCard(\"diagnosis\")"));
  assert.ok(source.includes("ClinicalAiDraftCard"));
  assert.ok(source.includes("Likely assessment"));
  assert.ok(source.includes("Differential diagnosis"));
  assert.ok(source.includes("Suggested investigations"));
  assert.ok(source.includes("Clinical Reasoning"));
  assert.ok(source.includes("Recommended Tests"));
});
