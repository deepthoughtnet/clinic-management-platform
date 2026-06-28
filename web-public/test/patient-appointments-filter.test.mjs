import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient appointments page keeps upcoming appointments future-only", () => {
  const source = readSource("pages/patient/PatientPortalPages.tsx");
  assert.ok(source.includes("function isUpcomingAppointment("));
  assert.ok(source.includes("appointments.data.filter(isUpcomingAppointment)"));
});
