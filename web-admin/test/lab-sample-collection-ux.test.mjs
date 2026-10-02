import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab sample collection auto-fills collected by and offers configured container and status selectors", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('const SAMPLE_CONTAINER_TYPE_OPTIONS = ['));
  assert.ok(source.includes('const sampleCollectingRef = React.useRef(false);'));
  assert.ok(source.includes('const closeSampleDialog = React.useCallback(() => {'));
  assert.ok(source.includes('const resetSampleDialogState = React.useCallback(() => {'));
  assert.ok(!source.includes('const canCollectSample = canUseLabReception || auth.hasPermission("lab.order.collect_sample");'));
  assert.ok(!source.includes('collectedBy: sampleCollectedBy.trim() || auth.username || auth.appUserId || null,'));
  assert.ok(source.includes('setSampleSuccessMessage('));
  assert.ok(source.includes('setSampleSuccessMessage(null);'));
  assert.ok(source.includes('InputProps={{ readOnly: true }}'));
  assert.ok(source.includes('Recorded from the signed-in user by the server.'));
  assert.ok(source.includes('<MenuItem value="">Select container type</MenuItem>'));
  assert.ok(source.includes('label="Collection Status"'));
  assert.ok(source.includes('selected: boolean;'));
  assert.ok(source.includes('Collect Selected'));
  assert.ok(source.includes('Collect All'));
  assert.ok(source.includes('sampleRows.filter((row) => row.selected)'));
  assert.ok(source.includes('Select at least one test/specimen to collect.'));
  assert.ok(source.includes('const activeLinkedItemIds = new Set('));
  assert.ok(source.includes('const uncollectedItems = row.items.filter((item) => !activeLinkedItemIds.has(item.id));'));
  assert.ok(source.includes('const sourceRows = recollectionSamples.length'));
  assert.ok(source.includes('function activeSpecimenItemIds(order: LabOrder | null | undefined)'));
  assert.ok(source.includes('const awaitingCollection = [...requiredItemIds].filter((itemId) => !collectedItemIds.has(itemId)).length;'));
  assert.ok(source.includes('function hasPendingCollection(order: LabOrder | null | undefined)'));
  assert.ok(!source.includes('if (row.status !== "READY_FOR_COLLECTION") return false;'));
});

test("lab order rows show compact sample audit chips", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('Collected by: ${row.sampleCollectedBy || row.sampleCollectedByUserId || "—"}'));
  assert.ok(source.includes('Date: ${formatDateChip(row.sampleCollectedAt)}'));
  assert.ok(source.includes('Time: ${formatTimeChip(row.sampleCollectedAt)}'));
  assert.ok(source.includes('Tests: {sampleLinkedTestsLabel(sample)}'));
  assert.ok(source.includes('row.samples.length > 1 ? ('));
  assert.ok(source.includes('Linked tests: {row.linkedTestNames.join(", ")}'));
});

test("dashboard counters still route into the correct work queues", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('work-pending-sample-collection'));
  assert.ok(source.includes('work-results-pending-entry'));
  assert.ok(source.includes('work-pending-lab-review'));
  assert.ok(source.includes('navigateLabTab("collection")'));
  assert.ok(source.includes('navigateLabTab("queue")'));
  assert.ok(source.includes('navigateLabTab("review")'));
});
