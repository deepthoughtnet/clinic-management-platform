import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource() {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", "pages", "consultations", "ClinicalDocumentFindingReviewPage.tsx"), "utf8");
}

test("finding review keeps actions available for every actionable pending status", () => {
  const source = readSource();

  assert.ok(source.includes('"PENDING_REVIEW", "PENDING_VERIFICATION", "PENDING", "REVIEW_REQUIRED", "AI_REVIEW_REQUIRED"'));
  assert.ok(source.includes(".replace(/[\\s-]+/g, \"_\")"));
  assert.ok(source.includes("Boolean(finding.id)"));
  assert.ok(source.includes("!finding.decision"));
  assert.ok(source.includes("reviewCompleted"));
  assert.ok(source.includes("Review completed"));
  assert.ok(source.includes("Original extracted"));
  assert.ok(source.includes("Doctor verified"));
  assert.ok(source.includes('"Edit"'));
  assert.ok(source.includes(">Reject</Button>"));
  assert.ok(source.includes('"Confirm"'));
});
