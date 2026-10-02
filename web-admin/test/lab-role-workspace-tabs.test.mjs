import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const page = await readFile(new URL("../src/pages/lab/LabPage.tsx", import.meta.url), "utf8");

test("laboratory tabs are derived from role ownership instead of raw permissions", () => {
  assert.match(page, /if \(isLabAssistantRole\) keys\.push\("collection"\)/);
  assert.match(page, /if \(isLabTechnicianRole\) keys\.push\("queue"\)/);
  assert.match(page, /if \(isLabApproverRole\) keys\.push\("review"\)/);
  assert.match(page, /activeLabTab === "queue"/);
  assert.match(page, /activeLabTab === "review"/);
});

test("dashboard quick actions use role-scoped action capabilities", () => {
  assert.match(page, /canCreateOrders: canCreateOrders && isLabFrontDeskRole/);
  assert.match(page, /canCollectPayment: canCollectPayment && isLabFrontDeskRole/);
  assert.match(page, /canCollectSample: canCollectSampleAction/);
  assert.match(page, /canEnterResults: canEnterResultsAction/);
  assert.match(page, /canReviewReport: canReviewReport && isLabApproverRole/);
  assert.match(page, /Received samples awaiting result entry will appear here/);
});

test("multi-specimen progress and receive work use active physical specimens", () => {
  assert.match(page, /function activeOrderSpecimens/);
  assert.match(page, /const activeIds = new Set/);
  assert.match(page, /function SpecimenProgressSummary/);
  assert.match(page, /Received: \{progress\.received\}\/\{progress\.total\}/);
  assert.match(page, /const eligible = activeOrderSpecimens\(order\)\.filter/);
  assert.match(page, /pendingCollectionCount = pendingSampleOrders\.length/);
});
