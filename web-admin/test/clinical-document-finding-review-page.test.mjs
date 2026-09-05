import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function source(...parts) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...parts), "utf8");
}

test("AI findings review is a dedicated consultation route", () => {
  const app = source("app", "App.tsx");
  const workspace = source("pages", "consultations", "ConsultationWorkspacePage.tsx");
  const page = source("pages", "consultations", "ClinicalDocumentFindingReviewPage.tsx");

  assert.ok(app.includes('path="/consultations/:id/ai-findings-review"'));
  assert.ok(app.includes("<ClinicalDocumentFindingReviewPage />"));
  assert.ok(workspace.includes("/ai-findings-review?documentId="));
  assert.ok(!workspace.includes("ClinicalDocumentFindingReviewDialog"));
  assert.ok(page.includes("getPatientDocuments(auth.accessToken, auth.tenantId, currentConsultation.patientId)"));
  assert.ok(!page.includes("{ consultationId }"));
  assert.ok(page.includes("getClinicalDocumentFindingReview"));
  assert.ok(page.includes("getPatientDocumentViewUrl"));
  assert.ok(page.includes("Pending"));
  assert.ok(page.includes("Completed"));
  assert.ok(page.includes("All"));
  assert.ok(page.includes("Review completed"));
  assert.ok(page.includes("reviewedByDisplayName"));
  assert.ok(page.includes("Original extracted"));
  assert.ok(page.includes("Doctor verified"));
  assert.ok(page.includes("Edit"));
  assert.ok(page.includes("Reject"));
  assert.ok(page.includes("Confirm"));
  assert.ok(page.includes("Complete Review"));
});
