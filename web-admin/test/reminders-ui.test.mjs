import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("reminders page renders sent metrics and business labels", () => {
  const source = readSource("products/carepilot/reminders/RemindersPage.tsx");
  const formatting = readSource("products/carepilot/shared/carepilotFormatting.ts");

  assert.ok(source.includes('const TAB_FILTERS = ["Upcoming", "Pending", "Sent", "Retrying", "Failed", "Delivered", "Read", "Skipped", "All"]'));
  assert.ok(source.includes('kpi("Sent", counts.sent)'));
  assert.ok(source.includes('campaignName}</Typography>'));
  assert.ok(source.includes('{row.campaignReference || "Unknown reference"}'));
  assert.ok(source.includes('row.patientReference || row.patientEmail || row.patientPhone || "No contact"'));
  assert.ok(source.includes('campaignTypeLabel(row.campaignType)'));
  assert.ok(source.includes('channelTypeLabel(row.channel)'));
  assert.ok(source.includes('humanizeCarePilotCode(row.executionStatus)'));
  assert.ok(source.includes('humanizeCarePilotCode(row.deliveryStatus)'));
  assert.ok(source.includes('humanizeCarePilotCode(attempt.deliveryStatus)'));
  assert.ok(source.includes('humanizeCarePilotCode(event.internalStatus)'));
  assert.ok(source.includes('humanizeSourceType(detail.timeline.execution.sourceType)'));
  assert.ok(source.includes('triggerTypeLabel(detail.reminder.triggerType)'));
  assert.ok(source.includes('Manual Campaign Run'));
  assert.ok(source.includes('Email'));
  assert.ok(source.includes('useCarePilotTenantTimezone'));
  assert.ok(source.includes('formatCarePilotDateTime'));
  assert.ok(source.includes('providerLabel(row.providerName)'));
  assert.ok(source.includes('providerLabel(attempt.providerName)'));
  assert.ok(source.includes('providerLabel(event.providerName)'));
  assert.ok(source.includes('formatCarePilotDateTime(row.scheduledAt, clinicTimeZone)'));
  assert.ok(source.includes('formatCarePilotDateTime(detail.reminder.scheduledAt, clinicTimeZone)'));
  assert.ok(source.includes('scroll="paper"'));
  assert.ok(source.includes('timelineContentRef'));
  assert.ok(source.includes('scrollTop = 0'));
  assert.ok(source.includes('scrollLeft = 0'));
  assert.ok(source.includes('detail?.reminder.executionId'));
  assert.ok(source.includes('More reminder actions'));
  assert.ok(source.includes('View Timeline'));
  assert.ok(source.includes('Open Patient'));
  assert.ok(source.includes('Open Campaign'));
  assert.ok(source.includes('detail.timeline.statusEvents.filter(shouldRenderTimelineEvent).map'));
  assert.ok(source.includes('aria-label="Close reminder timeline"'));
  assert.ok(source.includes('Close reminder timeline'));
  assert.ok(source.includes('onClick={closeDetail}'));
  assert.ok(source.includes('viewTimelineButtonRef.current?.focus()'));
  assert.ok(source.includes('DialogActions'));
  assert.ok(source.includes('Search by patient name or patient reference.'));
  assert.ok(!source.includes('label="Patient ID"'));
  assert.ok(!source.includes('>EMAIL<'));
  assert.ok(!source.includes('>SUCCEEDED<'));
  assert.ok(!source.includes('reminderReason'));
  assert.ok(!source.includes('function formatReminderDateTime'));
  assert.ok(!source.includes("alert("));
  assert.ok(!source.includes("prompt("));
  assert.ok(!source.includes("confirm("));

  assert.ok(formatting.includes('"carepilot email smtp": "Email SMTP"'));
  assert.ok(formatting.includes('"email smtp": "Email SMTP"'));
  assert.ok(formatting.includes('IST ('));
  assert.ok(formatting.includes('formatCarePilotDateTime'));
});

test("reminders api exposes campaign and patient business references", () => {
  const api = readSource("api/clinicApi.ts");

  assert.ok(api.includes("campaignReference: string | null;"));
  assert.ok(api.includes("patientReference: string | null;"));
  assert.ok(api.includes("reasonCode: string | null;"));
  assert.ok(api.includes("reasonLabel: string | null;"));
  assert.ok(api.includes("patientQuery?: string;"));
  assert.ok(api.includes('query.set("patientQuery", filters.patientQuery)'));
  assert.ok(api.includes('reasonCode: string;'));
  assert.ok(api.includes('reasonLabel: string;'));
});
