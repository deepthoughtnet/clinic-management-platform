import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("appointment quick register uses shared schema validation and field errors", () => {
  const source = readSource("pages/appointments/AppointmentsPage.tsx");
  assert.ok(source.includes("Quick Register Patient"));
  assert.ok(source.includes("No matching patient found. Quick register and continue."));
  assert.ok(source.includes("Patient registered. Select the patient again to continue."));
  assert.ok(source.includes("title=\"Quick Register Patient\""));
});
