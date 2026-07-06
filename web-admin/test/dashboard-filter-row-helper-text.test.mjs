import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? path.join(process.cwd(), "web-admin") : process.cwd();
}

function readSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "src", ...relPath.split("/")), "utf8");
}

test("dashboard filter row removes default helper text and keeps validation tooltips", () => {
  const source = readSource("pages/DashboardPage.tsx");

  assert.ok(source.includes("Tooltip"));
  assert.ok(source.includes('title={dateFieldErrors.startDate || ""}'));
  assert.ok(source.includes('title={dateFieldErrors.endDate || ""}'));
  assert.ok(source.includes("helperText={dateFieldErrors.startDate || undefined}"));
  assert.ok(source.includes("helperText={dateFieldErrors.endDate || undefined}"));
  assert.ok(!source.includes('helperText={dateFieldErrors.startDate || "Required."}'));
  assert.ok(!source.includes('helperText={dateFieldErrors.endDate || "Must be on or after start."}'));
  assert.ok(!source.includes('"Required."'));
  assert.ok(!source.includes('"Must be on or after start."'));
});
