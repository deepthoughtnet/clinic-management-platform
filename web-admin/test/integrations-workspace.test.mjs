import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("integrations page hides raw configuration keys from clinic admin guidance", () => {
  const page = readSource("pages/admin/IntegrationsPage.tsx");
  const api = readSource("api/clinicApi.ts");

  assert.ok(page.includes("Guidance for Clinic Admin"));
  assert.ok(page.includes("Technical details"));
  assert.ok(page.includes("Platform Admin only"));
  assert.ok(page.includes("Open Messaging"));
  assert.ok(page.includes("Notification Settings"));
  assert.ok(page.includes("Templates"));
  assert.ok(page.includes("Refresh"));
  assert.ok(page.includes("Test not available yet"));
  assert.ok(page.includes("canSeeTechnicalDetails"));
  assert.ok(!page.includes("CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED"));
  assert.ok(!page.includes("SPRING_MAIL_HOST"));
  assert.ok(!page.includes("CLINIC_AI_GEMINI_API_KEY"));
  assert.ok(!page.includes("clinic.carepilot.messaging.whatsapp.access-token"));
  assert.ok(api.includes("export type AdminIntegrationStatusRow"));
  assert.ok(api.includes("safeConfigurationHints"));
  assert.ok(api.includes("missingConfigurationKeys"));
});
