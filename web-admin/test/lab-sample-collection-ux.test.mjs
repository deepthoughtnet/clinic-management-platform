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
  assert.ok(source.includes('const SAMPLE_COLLECTION_STATUS_OPTIONS = ['));
  assert.ok(source.includes('const [sampleCollectionStatus, setSampleCollectionStatus] = React.useState<SampleCollectionStatus>("Collected");'));
  assert.ok(source.includes('setSampleCollectedBy(auth.username || auth.appUserId || "");'));
  assert.ok(source.includes('setSampleCollectionStatus("Collected");'));
  assert.ok(!source.includes('collectedBy: sampleCollectedBy.trim() || auth.username || auth.appUserId || null,'));
  assert.ok(source.includes('InputProps={{ readOnly: true }}'));
  assert.ok(source.includes('Auto-populated from the signed-in user and sent by the server audit trail.'));
  assert.ok(source.includes('<MenuItem value="">Select container type</MenuItem>'));
  assert.ok(source.includes('label="Collection Status"'));
});

test("lab order rows show compact sample audit chips", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('Collected by: ${row.sampleCollectedBy || row.sampleCollectedByUserId || "—"}'));
  assert.ok(source.includes('Date: ${formatDateChip(row.sampleCollectedAt)}'));
  assert.ok(source.includes('Time: ${formatTimeChip(row.sampleCollectedAt)}'));
});

test("dashboard counters still route into the correct work queues", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('work-pending-sample-collection'));
  assert.ok(source.includes('work-results-pending-entry'));
  assert.ok(source.includes('work-pending-lab-review'));
  assert.ok(source.includes('setTab(1);'));
  assert.ok(source.includes('setTab(canEnterResults ? 2 : 3);'));
  assert.ok(source.includes('setTab(3);'));
});
