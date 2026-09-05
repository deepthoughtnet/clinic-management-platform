import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("doctor workspace footer renders a doctor-gated AI disclaimer with full disclosure dialog", () => {
  const source = readSource("layout/Footer.tsx");
  const shellSource = readSource("layout/AppShell.tsx");

  assert.ok(source.includes("AI assistance only. Jeevanam AI-generated clinical suggestions"));
  assert.ok(source.includes("View full AI disclaimer"));
  assert.ok(source.includes("AI Clinical Assistance Disclaimer"));
  assert.ok(source.includes("the treating clinician remains responsible for final clinical decisions") || source.includes("remains responsible for final clinical decisions"));
  assert.ok(source.includes("Dialog"));
  assert.ok(source.includes("onClick={handleDisclaimerOpen}"));
  assert.ok(source.includes("onClose={handleDisclaimerClose}"));
  assert.ok(source.includes('rolesUpper.includes("DOCTOR")'));
  assert.ok(source.includes("DOCTOR_WORKSPACE_PATH_RE"));
  assert.ok(source.includes('hasTenantModule(auth, "aiCopilot")'));
  assert.ok(source.includes("AI Assist:"));
  assert.ok(source.includes("AI assistance is turned off"));
  assert.ok(source.includes("Switch"));
  assert.ok(shellSource.includes("<Footer />"));
});
