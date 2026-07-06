import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("doctor queue prioritizes consultation actions over patient fallback", () => {
  const source = readSource("pages/appointments/QueuePage.tsx");

  assert.ok(source.includes("resolveDoctorPrimaryAction"));
  assert.ok(source.includes('status === "CHECKED_IN"'));
  assert.ok(source.includes("Start Consultation"));
  assert.ok(source.includes("Continue Consultation"));
  assert.ok(source.includes("View Summary"));
  assert.ok(source.includes("Open Patient"));
  assert.ok(source.includes("canUseClinicalIntake"));
  assert.ok(source.includes('clinical-intake'));
  assert.ok(source.includes("Showing only queue items assigned to you."));
});
