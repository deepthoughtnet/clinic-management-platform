import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const page = await readFile(new URL("../src/pages/lab/LabPage.tsx", import.meta.url), "utf8");

test("lab action capabilities are role-scoped", () => {
  assert.match(page, /canCollectSampleAction\s*=\s*canCollectSample\s*&&\s*isLabAssistantRole/);
  assert.match(page, /canReceiveRejectSampleAction\s*=\s*canCollectSample\s*&&\s*isLabTechnicianRole/);
  assert.match(page, /canEnterResultsAction\s*=\s*canEnterResults\s*&&\s*isLabTechnicianRole/);
  assert.match(page, /canManageSamples=\{canReceiveRejectSampleAction\}/);
  assert.match(page, /canEnterResults=\{canEnterResultsAction\}/);
  assert.doesNotMatch(page, /canManageSamples=\{canCollectSample\}/);
});

test("sample action controls retain lifecycle state guards", () => {
  assert.match(page, /const canReceiveSample = sample\.status === "COLLECTED"/);
  assert.match(page, /const canRejectSample = sample\.status === "COLLECTED" \|\| sample\.status === "RECEIVED"/);
});
