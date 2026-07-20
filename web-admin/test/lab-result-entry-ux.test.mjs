import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab result entry supports draft save, resume, preview, and review queue handoff", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('const [resultDraftLoaded, setResultDraftLoaded] = React.useState(false);'));
  assert.ok(source.includes('const [resultSavingDraft, setResultSavingDraft] = React.useState(false);'));
  assert.ok(source.includes('const [resultSaveMessage, setResultSaveMessage] = React.useState<string | null>(null);'));
  assert.ok(source.includes('lab.result-entry.draft.'));
  assert.ok(source.includes('Draft restored from your last save.'));
  assert.ok(source.includes('Save Draft'));
  assert.ok(source.includes('Save Results'));
  assert.ok(source.includes('Results saved. Order moved to Pending Lab Review.'));
  assert.ok(source.includes('setTab(3);'));
  assert.ok(source.includes('getLabOrderAttachmentBlob'));
  assert.ok(source.includes('Attachments'));
  assert.ok(source.includes('Preview is available for images and PDF files.'));
  assert.ok(source.includes('Ref ${formatReferenceBadge(item.referenceRange)} • Crit ${formatReferenceBadge(item.criticalRange)}'));
  assert.ok(source.includes('error={severity === "error"}'));
  assert.ok(source.includes('sx={resultFieldSx(severity)}'));
});

test("lab result entry retains existing approval flow and report generation", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('openReviewDialog'));
  assert.ok(source.includes('saveReview'));
  assert.ok(source.includes('publishLabOrderReport'));
  assert.ok(source.includes('Verify Results'));
});
