import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("notification operations workspace is wired into navigation, routes, tabs, and APIs", () => {
  const page = readSource("pages/admin/NotificationOperationsPage.tsx");
  const model = readSource("pages/admin/notificationOperationsModel.ts");
  const nav = readSource("layout/nav.ts");
  const sidebar = readSource("layout/SidebarNav.tsx");
  const app = readSource("app/App.tsx");
  const api = readSource("api/clinicApi.ts");

  assert.ok(page.includes("Notification Operations"));
  assert.ok(page.includes("Deliveries"));
  assert.ok(page.includes("Failures & Retries"));
  assert.ok(page.includes("Providers"));
  assert.ok(page.includes("Analytics"));
  assert.ok(page.includes("Audit"));
  assert.ok(page.includes("useSearchParams"));
  assert.ok(page.includes("getNotificationOperationsSummary"));
  assert.ok(page.includes("getNotificationOperationsDeliveries"));
  assert.ok(page.includes("getNotificationOperationsFailures"));
  assert.ok(page.includes("getNotificationOperationsProviders"));
  assert.ok(page.includes("getNotificationOperationsAnalytics"));
  assert.ok(page.includes("getNotificationOperationsAudit"));
  assert.ok(page.includes("bulkRetryNotificationOperations"));
  assert.ok(page.includes("ConfirmationDialog"));
  assert.ok(page.includes("channelSummaryLabel"));
  assert.ok(page.includes("channelDisplayStatus"));
  assert.ok(page.includes("Notification Success"));
  assert.ok(page.includes("Healthy Providers"));
  assert.ok(page.includes("Failed Deliveries"));
  assert.ok(page.includes("Pending Retry"));
  assert.ok(page.includes("Detailed metrics"));
  assert.ok(page.includes("Average Latency"));
  assert.ok(page.includes("Provider readiness"));
  assert.ok(page.includes("Operational success"));
  assert.ok(page.includes("Healthy notification deliveries"));
  assert.ok(page.includes("Technical counts that support operational review."));
  assert.ok(page.includes("Drawer"));
  assert.ok(page.includes("Notification Operations"));

  assert.ok(model.includes("NOTIFICATION_OPERATION_TABS"));
  assert.ok(model.includes("NOTIFICATION_OPERATION_PERIODS"));
  assert.ok(model.includes('label: "Overview"'));
  assert.ok(model.includes("normalizeNotificationReason"));
  assert.ok(model.includes("channelDisplayStatus"));
  assert.ok(model.includes("overallStatusLabel"));
  assert.ok(model.includes("channelSummaryLabel"));

  assert.ok(nav.includes('label: "Notification Operations"'));
  assert.ok(nav.includes('path: "/admin/notification-operations"'));
  assert.ok(sidebar.includes('"notification-operations"'));
  assert.ok(app.includes('path="/admin/notification-operations"'));
  assert.ok(app.includes("NotificationOperationsGate"));

  assert.ok(api.includes("export type NotificationOperationsSummaryResponse"));
  assert.ok(api.includes("export async function getNotificationOperationsSummary"));
  assert.ok(api.includes("export async function getNotificationOperationsDeliveries"));
  assert.ok(api.includes("export async function getNotificationOperationsFailures"));
  assert.ok(api.includes("export async function getNotificationOperationsProviders"));
  assert.ok(api.includes("export async function getNotificationOperationsAnalytics"));
  assert.ok(api.includes("export async function getNotificationOperationsAudit"));
  assert.ok(api.includes("export async function bulkRetryNotificationOperations"));
});

test("notification operations model normalizes channel and failure labels", () => {
  const model = readSource("pages/admin/notificationOperationsModel.ts");

  assert.ok(model.includes('clinic.carepilot.messaging.sms.enabled=false'));
  assert.ok(model.includes('clinic.carepilot.messaging.whatsapp.enabled=false'));
  assert.ok(model.includes("Patient record unavailable"));
  assert.ok(model.includes("Patient email unavailable"));
  assert.ok(model.includes("SMS notifications disabled"));
  assert.ok(model.includes("WhatsApp notifications disabled"));
  assert.ok(model.includes("channelSummaryLabel"));
  assert.ok(model.includes("IN_APP"));
  assert.ok(model.includes("EMAIL"));
  assert.ok(model.includes("SMS"));
  assert.ok(model.includes("WHATSAPP"));
});
