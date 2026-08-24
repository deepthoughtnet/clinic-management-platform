import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab verification dialog renders the full read-only result set before controls", () => {
  const source = readSource("pages/lab/LabPage.tsx");

  assert.ok(source.includes('function hasReviewableOrderedTests(order: LabOrder | null | undefined) {'));
  assert.ok(source.includes('function hasPublishableOrderedTests(order: LabOrder | null | undefined) {'));
  assert.ok(source.includes('const pendingReviewOrders = React.useMemo(() => orders.filter((row) => hasReviewableOrderedTests(row)), [orders]);'));
  assert.ok(source.includes('const readyToPublishCount = orders.filter((row) => hasPublishableOrderedTests(row)).length;'));
  assert.ok(source.includes('const reviewResultGroups = React.useMemo(() => buildReviewResultGroups(reviewTarget), [reviewTarget]);'));
  assert.ok(source.includes('const reviewHasCriticalResults = React.useMemo('));
  assert.ok(source.includes('const reviewOrderedTests = React.useMemo('));
  assert.ok(source.includes('() => reviewTarget?.orderedTests || [],'));
  assert.ok(source.includes('Technician entry metadata'));
  assert.ok(source.includes('Technician comment'));
  assert.ok(source.includes('Critical result present'));
  assert.ok(source.includes('Critical result rows are highlighted below. Review the complete result set before approving.'));
  assert.ok(source.includes('Parameter / component'));
  assert.ok(source.includes('Reference range'));
  assert.ok(source.includes('Critical range'));
  assert.ok(source.includes('Entered by'));
  assert.ok(source.includes('Entered at'));
  assert.ok(source.includes('No result rows are available for this order.'));
  assert.ok(source.includes('Read only'));
  assert.ok(source.includes('Not selectable'));
  assert.ok(source.includes('resultTone(row.resultFlag)'));
  assert.ok(source.includes('resultFlagLabel(row.resultFlag)'));
  assert.ok(source.includes('reviewTarget?.resultComments'));
  assert.ok(source.includes('Table size="small" aria-label={`${group.testName} results`}'));
  assert.ok(source.includes('Verify Results'));
  assert.ok(source.includes('Send back'));
  assert.ok(source.includes('LAB_RESULT_CORRECTION'));
  assert.ok(source.includes('Result correction reason'));
  assert.ok(source.includes('Optional comments for the technician.'));
  assert.ok(source.includes('LAB_SPECIMEN_REJECTION'));
  assert.ok(source.includes('Specimen rejection reason'));
  assert.ok(source.includes('Continue Review'));
  assert.ok(source.includes('Publish report'));
});

test("lab API response exposes result entry metadata for verification rendering", () => {
  const source = readSource("api/clinicApi.ts");

  assert.ok(source.includes('resultEnteredAt: string | null;'));
  assert.ok(source.includes('resultEnteredByUserId: string | null;'));
  assert.ok(source.includes('resultEnteredBy: string | null;'));
});
