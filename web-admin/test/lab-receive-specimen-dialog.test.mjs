import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const page = await readFile(new URL("../src/pages/lab/LabPage.tsx", import.meta.url), "utf8");

test("receive dialog is specimen-scoped and supports selected/all actions", () => {
  assert.match(page, /Receive Samples/);
  assert.match(page, /Receive Selected/);
  assert.match(page, /Receive All/);
  assert.match(page, /receiveAtById/);
  assert.match(page, /sample\.linkedTestNames\.join/);
  assert.match(page, /sample\.status === "COLLECTED"/);
  assert.match(page, /onReceiveSample\(row, sample\)/);
});

test("receive dialog keeps server-derived receiver read-only", () => {
  assert.match(page, /Received By<\/Typography>/);
  assert.match(page, /auth\.username \|\| auth\.appUserId \|\| "Signed-in technician"/);
  assert.match(page, /receiveLabSample\(auth\.accessToken, auth\.tenantId, sample\.id/);
});

test("orders expose receive when any active specimen is collected", () => {
  assert.match(page, /function hasReceivableSpecimen\(order: LabOrder \| null \| undefined\)/);
  assert.match(page, /return activeOrderSpecimens\(order\)\.filter\(\(sample\) => sample\.status === "COLLECTED"\)/);
  assert.match(page, /canReceiveRejectSampleAction && hasReceivableSpecimen\(row\)/);
  assert.match(page, /onClick=\{\(\) => openReceiveDialog\(row\)\}/);
  assert.doesNotMatch(page, /canReceiveRejectSampleAction && sampleAction\?\.status === "COLLECTED"/);
});

test("multi-specimen reject is not an ambiguous whole-order action", () => {
  assert.match(page, /canReceiveRejectSampleAction && row\.samples\.length <= 1 && sampleAction/);
});

test("orders expose result entry independently of receive for multi-specimen rows", () => {
  assert.match(page, /canEnterResults && hasTechnicianWork\(row\)/);
  assert.match(page, /onClick=\{\(\) => onEnterResults\(row\)\}/);
  assert.doesNotMatch(page, /canEnterResults && hasTechnicianWork\(row\) && row\.samples\.length <= 1/);
});
