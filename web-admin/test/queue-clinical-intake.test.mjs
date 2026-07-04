import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("queue page exposes clinical intake action and status badges", () => {
  const source = readSource("pages/appointments/QueuePage.tsx");
  assert.ok(source.includes("Clinical Intake"));
  assert.ok(source.includes("Intake complete"));
  assert.ok(source.includes("Pending intake"));
});
