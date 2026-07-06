import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readWebAdminSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("doctor availability operational calendar header keeps avatar top-right without divider", () => {
  const source = readWebAdminSource("pages/doctors/DoctorAvailabilityPage.tsx");

  assert.ok(source.includes("Operational Calendar"));
  assert.ok(source.includes('display: "flex"'));
  assert.ok(source.includes('justifyContent: "space-between"'));
  assert.ok(source.includes('alignItems: "flex-start"'));
  assert.ok(source.includes('ml: "auto"'));
  assert.ok(source.includes('<DoctorIdentityCard doctorId={selectedDoctorIdentity.id} doctor={selectedDoctorIdentity} variant="avatar" />'));
  assert.ok(!source.includes('borderLeft: { xs: "none", lg: "1px solid" }'));
  assert.ok(!source.includes('Select a doctor to view profile context.'));
});
