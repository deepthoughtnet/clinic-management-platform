import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? path.join(process.cwd(), "web-admin") : process.cwd();
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation patient snapshot long-term medications uses verified longitudinal memory only", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("const clinicalSnapshotMedications = verifiedLongTermMedications.length"));
  assert.ok(source.includes(': "Not recorded";'));
  assert.ok(!source.includes('patientRow?.longTermMedications || "Not recorded"'));
});
