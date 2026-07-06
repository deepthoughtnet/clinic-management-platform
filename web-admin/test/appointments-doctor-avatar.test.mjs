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

test("appointments page shows selected doctor avatar in slot header", () => {
  const source = readSource("pages/appointments/AppointmentsPage.tsx");

  assert.ok(source.includes("DoctorIdentityCard"));
  assert.ok(source.includes('variant="avatar"'));
  assert.ok(source.includes("getDoctorProfile"));
  assert.ok(source.includes("selectedDoctorIdentity"));
  assert.ok(source.includes("selectedDoctorProfile"));
  assert.ok(source.includes("Doctor Available Slots"));
  assert.ok(source.includes('gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 1fr) auto" }'));
});
