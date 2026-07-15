import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation completion uses grouped readiness instead of a flat 13-item score", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("Completion readiness"));
  assert.ok(source.includes("consultationCompletionSummary.groups.map"));
  assert.ok(source.includes("Required to complete"));
  assert.ok(source.includes("Clinical documentation"));
  assert.ok(source.includes("When applicable / Optional outputs"));
  assert.ok(source.includes("Documentation"));
  assert.ok(source.includes("Optional"));
  assert.ok(source.includes("Ready to complete"));
  assert.ok(source.includes("Review recommended"));
  assert.ok(source.includes("Blocked"));
  assert.ok(!source.includes("${consultationCompletionSummary.complete}/${consultationCompletionChecklist.length} complete"));
  assert.ok(!source.includes("consultationCompletionChecklist.slice(0, 6)"));
  assert.ok(!source.includes("complete}/${consultationCompletionChecklist.length}"));
});

test("consultation completion drawer and top guide use distinct semantics", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("Clinical Documentation Guide"));
  assert.ok(source.includes("Guidance only. This does not change completion rules."));
  assert.ok(source.includes("View guide"));
  assert.ok(source.includes("Completion Validation"));
  assert.ok(source.includes("View validation"));
  assert.ok(source.includes("Completion readiness"));
  assert.ok(source.includes("Final completion is blocked only by prescription readiness."));
});

test("consultation completion groups treat optional outputs as neutral and advisory documentation as non-blocking", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes('label: "Prescription"'));
  assert.ok(source.includes('stateLabel: prescriptionReadyForCompletion ? "Ready" : "Not finalized"'));
  assert.ok(source.includes('label: "Chief Complaint"'));
  assert.ok(source.includes('stateLabel: consultationForm.diagnosis.trim()'));
  assert.ok(source.includes('Review recommended'));
  assert.ok(source.includes('Not recorded'));
  assert.ok(source.includes('label: "Investigations"'));
  assert.ok(source.includes('stateLabel: recommendedTestSuggestions.length ? "Available" : "Not applicable"'));
  assert.ok(source.includes('label: "Lab Orders"'));
  assert.ok(source.includes('stateLabel: labOrders.length ? "Added" : (recommendedTestSuggestions.length ? "Not added" : "Not applicable")'));
  assert.ok(source.includes('label: "Referral"'));
  assert.ok(source.includes('stateLabel: hasReferral ? "Generated" : "Not created"'));
  assert.ok(source.includes('label: "Certificate"'));
  assert.ok(source.includes('stateLabel: hasCertificate ? "Generated" : "Not created"'));
  assert.ok(source.includes('stateLabel: hasInstructions ? "Prepared" : "Not generated"'));
});

test("consultation completion CTA shows the exact blocker and the ready-to-complete message", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("Finalize the prescription before completing this consultation."));
  assert.ok(source.includes("Consultation is ready for completion."));
  assert.ok(source.includes("disabled={saving || !prescriptionReadyForCompletion}"));
  assert.ok(source.includes("prescriptionReadyForCompletion ? \"Ready\" : \"Not finalized\""));
});
