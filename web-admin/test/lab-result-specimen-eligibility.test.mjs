import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const page = await readFile(new URL("../src/pages/lab/LabPage.tsx", import.meta.url), "utf8");

test("result entry is filtered by the test's active received specimen", () => {
  assert.match(page, /function hasReceivedActiveSpecimenForTest/);
  assert.match(page, /link\.active/);
  assert.match(page, /String\(link\.sampleStatus \|\| \"\"\)\.toUpperCase\(\) === \"RECEIVED\"/);
  assert.match(page, /hasReceivedActiveSpecimenForTest\(order, orderedTest, sampleId\)/);
  assert.match(page, /No active received specimens are ready for result entry/);
});

test("result actions use specimen-aware eligibility rather than order-level receipt", () => {
  assert.match(page, /editableResultOrderedTests\(row, sample\.id\)\.length/);
  assert.match(page, /const eligibleIds = new Set\(editableResultOrderedTests\(row, scopeSampleId\)/);
  assert.doesNotMatch(page, /hasReceivedResultSample\(order\) && \(hasInitialResultEntryWork\(order\) \|\| hasCorrectionWork\(order\)\)/);
});

test("technician work queue and Orders action share received result eligibility", () => {
  assert.match(page, /function hasTechnicianWork\(order: LabOrder \| null \| undefined\)/);
  assert.match(page, /const pendingResultsOrders = React\.useMemo\(\(\) => orders\.filter\(\(row\) => hasTechnicianWork\(row\)/);
  assert.match(page, /canEnterResults && hasTechnicianWork\(row\)/);
});
