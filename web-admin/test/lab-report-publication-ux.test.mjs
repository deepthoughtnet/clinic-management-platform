import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab report publish dialog defaults to patient portal only and returns to lab orders", () => {
  const source = readSource("pages/lab/LabPage.tsx");

  assert.ok(source.includes('setPublishChannels(["PATIENT_PORTAL"]);'));
  assert.ok(source.includes("Requested channels"));
  assert.ok(source.includes("Recorded delivery actions"));
  assert.ok(source.includes("Back to Lab Orders"));
  assert.ok(source.includes("setTab(4);"));
  assert.ok(!source.includes('...(row.requestedByInternalDoctorId || row.consultationId ? ["DOCTOR_NOTIFICATION"] : [])'));
});
