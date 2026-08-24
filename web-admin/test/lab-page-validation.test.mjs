import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab editor keeps test code required, read-only on edit, and uses the shared required label", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('label={<RequiredLabel text="Test Code" required />}'));
  assert.ok(source.includes('disabled={Boolean(editing)}'));
  assert.ok(source.includes('InputProps={{ readOnly: Boolean(editing) }}'));
  assert.ok(source.includes('label={<RequiredLabel text="Test Name" required />}'));
  assert.ok(source.includes('label={<RequiredLabel text="Price" required />}'));
  assert.ok(source.includes('label="Turnaround Time (Hours)"'));
});

test("lab result entry scopes by specimen and sends the selected accession id", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('labOrderSampleId: resultScopeSampleId'));
  assert.ok(source.includes('editableResultOrderedTests(row, sample.id).length'));
  assert.ok(source.includes('onClick={() => onEnterResults(row, sample)}'));
  assert.ok(source.includes('row.samples.length <= 1'));
});
