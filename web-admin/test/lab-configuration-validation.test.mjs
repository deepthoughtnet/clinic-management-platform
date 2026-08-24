import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab configuration uses corrected nonnegative messages for overrides and display order", () => {
  const source = readSource("pages/lab/LabConfigurationPanel.tsx");
  assert.ok(source.includes('Display order must be zero or greater.'));
  assert.ok(source.includes('Price override must be zero or greater.'));
  assert.ok(source.includes('TAT override must be a whole number between 0 and 999.'));
  assert.ok(source.includes('Price override must have at most 2 decimal places.'));
});

