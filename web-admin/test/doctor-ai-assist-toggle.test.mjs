import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("doctor AI assist preference is persisted locally and gates consultation AI surfaces", () => {
  const footerSource = readSource("layout/Footer.tsx");
  const pageSource = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const hookSource = readSource("hooks/useDoctorAiAssistPreference.ts");

  assert.ok(hookSource.includes("doctor.ai-assist"));
  assert.ok(hookSource.includes("window.localStorage"));
  assert.ok(hookSource.includes("useDoctorAiAssistPreference"));
  assert.ok(footerSource.includes("useDoctorAiAssistPreference"));
  assert.ok(footerSource.includes('hasTenantModule(auth, "aiCopilot")'));
  assert.ok(footerSource.includes("AI Assist:"));
  assert.ok(footerSource.includes("AI assistance is turned off"));
  assert.ok(footerSource.includes("View full AI disclaimer"));
  assert.ok(pageSource.includes("useDoctorAiAssistPreference"));
  assert.ok(pageSource.includes('const tenantHasAiCopilot = hasTenantModule(auth, "aiCopilot")'));
  assert.ok(pageSource.includes("doctorAiAssistEnabled"));
  assert.ok(pageSource.includes("aiAssistantVisible"));
  assert.ok(pageSource.includes("tenantHasAiCopilot ? CONSULTATION_TAB_KEYS"));
  assert.ok(pageSource.includes("tenantHasAiCopilot && activeTab === 5"));
  assert.ok(pageSource.includes("if (!tenantHasAiCopilot)"));
  assert.ok(pageSource.includes("AI assistance is turned off"));
  assert.ok(pageSource.includes("Re-enable AI assistance from the workspace footer"));
  assert.ok(!pageSource.includes("manual mode"));
});
