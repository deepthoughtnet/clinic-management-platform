import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("notification configuration center groups settings by business domain and persists the policy JSON", () => {
  const page = readSource("pages/admin/NotificationSettingsPage.tsx");
  const model = readSource("pages/admin/notificationSettingsModel.ts");
  const api = readSource("api/clinicApi.ts");

  assert.ok(page.includes("Notification Configuration Center"));
  assert.ok(page.includes("Channel Configuration"));
  assert.ok(page.includes("Provider Health"));
  assert.ok(page.includes("Notification Matrix"));
  assert.ok(page.includes("Quiet Hours"));
  assert.ok(page.includes("Compliance / Consent"));
  assert.ok(page.includes("Rate Limits"));
  assert.ok(page.includes("Configuration Ready"));
  assert.ok(page.includes("Not configured"));
  assert.ok(page.includes("Patient-level preferences override tenant defaults."));
  assert.ok(page.includes("Critical alerts bypass quiet hours."));
  assert.ok(page.includes("Template changes affect future notifications only."));
  assert.ok(page.includes("Rate limits define policy."));
  assert.ok(page.includes("Apply Quiet Hours To"));
  assert.ok(page.includes("Open Template"));
  assert.ok(page.includes("notificationPolicyJson"));
  assert.ok(page.includes('setSearchParams({ section: next }, { replace: true })'));
  assert.ok(page.includes('disabled={!canMutate || (!row.emailReady && !row.emailEnabled)}'));
  assert.ok(page.includes('disabled={!canMutate || (!row.smsReady && !row.smsEnabled)}'));
  assert.ok(page.includes('disabled={!canMutate || (!row.whatsappReady && !row.whatsappEnabled)}'));

  for (const title of [
    "Appointments",
    "Billing",
    "Clinical",
    "Laboratory",
    "Vaccination",
    "Engage",
    "System",
  ]) {
    assert.ok(model.includes(`title: "${title}"`), `missing section ${title}`);
  }

  assert.ok(model.includes("createDefaultNotificationPolicy"));
  assert.ok(model.includes("parseNotificationPolicy"));
  assert.ok(model.includes("serializeNotificationPolicy"));
  assert.ok(model.includes("QUIET_HOUR_SCOPE_OPTIONS"));
  assert.ok(model.includes("DEFAULT_RATE_LIMITS"));
  assert.ok(model.includes("DEFAULT_COMPLIANCE"));
  assert.ok(model.includes('key: "appointments"'));
  assert.ok(model.includes('key: "billing"'));
  assert.ok(model.includes('key: "clinical"'));
  assert.ok(model.includes('key: "laboratory"'));
  assert.ok(model.includes('key: "vaccination"'));
  assert.ok(model.includes('key: "engage"'));
  assert.ok(model.includes('key: "system"'));
  assert.ok(model.includes('bookingConfirmation'));
  assert.ok(model.includes('paymentReminder'));
  assert.ok(model.includes('prescriptionReady'));
  assert.ok(model.includes('reportReady'));
  assert.ok(model.includes('vaccinationDue'));
  assert.ok(model.includes('leadFollowUp'));
  assert.ok(model.includes('platformAlerts'));

  assert.ok(api.includes("notificationPolicyJson: string;"));
});

test("templates page supports deep-link filtering from the notification settings page", () => {
  const page = readSource("pages/admin/TemplatesPage.tsx");

  assert.ok(page.includes("useSearchParams"));
  assert.ok(page.includes('searchParams.get("templateType")'));
  assert.ok(page.includes('searchParams.get("channel")'));
  assert.ok(page.includes('searchParams.get("category")'));
  assert.ok(page.includes('searchParams.get("active")'));
  assert.ok(page.includes("listAdminTemplates(auth.accessToken, auth.tenantId, {"));
  assert.ok(page.includes("templateType: typeFilter ?"));
  assert.ok(page.includes("channel: channelFilter ?"));
  assert.ok(page.includes("category: categoryFilter ?"));
});
