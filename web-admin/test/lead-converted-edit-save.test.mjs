import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import {
  buildConvertedLeadMetadataPayload,
  hasConvertedLeadMetadataChanges,
  toConvertedLeadMetadataSnapshot,
} from "../src/products/carepilot/leads/leadFormUtils.js";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

const baseline = {
  notes: "Old note",
  tags: "lead, vip",
  sourceDetails: "webinar",
  campaignId: "camp-1",
  assignedToAppUserId: "user-1",
};

test("converted lead metadata snapshot normalizes tags and spacing", () => {
  const snapshot = toConvertedLeadMetadataSnapshot({
    notes: "  Old note  ",
    tags: " vip , lead , ",
    sourceDetails: " webinar ",
    campaignId: " camp-1 ",
    assignedToAppUserId: " user-1 ",
  });

  assert.deepEqual(snapshot, baseline);
});

test("converted lead save stays pristine until permitted fields change", () => {
  assert.equal(hasConvertedLeadMetadataChanges(baseline, baseline), false);
  assert.equal(hasConvertedLeadMetadataChanges({ ...baseline, notes: "Old note" }, baseline), false);
  assert.equal(hasConvertedLeadMetadataChanges({ ...baseline, tags: "vip, lead" }, baseline), false);
  assert.equal(hasConvertedLeadMetadataChanges({ ...baseline, tags: "vip, lead, follow up" }, baseline), true);
});

test("converted lead metadata payload only includes changed fields", () => {
  const payload = buildConvertedLeadMetadataPayload(
    { ...baseline, notes: "New note", tags: "vip, lead", sourceDetails: "webinar", campaignId: "camp-1", assignedToAppUserId: "user-1" },
    baseline,
  );

  assert.deepEqual(payload, { notes: "New note" });
});

test("converted lead metadata payload can isolate assignee-only updates", () => {
  const payload = buildConvertedLeadMetadataPayload(
    { ...baseline, assignedToAppUserId: "user-2" },
    baseline,
  );

  assert.deepEqual(payload, { assignedToAppUserId: "user-2" });
});

test("converted lead detail is read-only and no longer exposes a converted edit save path", () => {
  const page = readSource("products/carepilot/leads/LeadsPage.tsx");

  assert.ok(page.includes('convertedLead ? "Converted Lead Details" : "Lead Detail"'));
  assert.ok(page.includes('Read-only after conversion.'));
  assert.ok(page.includes('canPersistLeadForm ? ('));
  assert.ok(page.includes('canAddLeadNote'));
  assert.ok(!page.includes("updateCarePilotConvertedLeadMetadata"));
  assert.ok(!page.includes("buildConvertedLeadMetadataPayload"));
  assert.ok(!page.includes("convertedLeadDirty"));
  assert.ok(!page.includes("convertedLeadCanSave"));
});
