import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("ops console uses persisted attempt counts, shared labels, and accessible timeline modal", () => {
  const page = readSource("products/carepilot/ops/OpsConsolePage.tsx");
  const formatting = readSource("products/carepilot/shared/carepilotFormatting.ts");

  assert.ok(page.includes('useCarePilotTenantTimezone'));
  assert.ok(page.includes('formatCarePilotDateTime'));
  assert.ok(page.includes('formatCarePilotDurationMinutes'));
  assert.ok(page.includes('humanizeCarePilotCode'));
  assert.ok(page.includes('providerLabel'));
  assert.ok(page.includes('channelTypeLabel'));
  assert.ok(page.includes('row.deliveryAttemptCount'));
  assert.ok(page.includes('row.retryCount'));
  assert.ok(page.includes('Delivery Attempt Count:'));
  assert.ok(page.includes('Retry Count:'));
  assert.ok(page.includes('Delivery Attempts'));
  assert.ok(page.includes('providerLabel(row.providerName)'));
  assert.ok(page.includes('providerLabel(attempt.providerName)'));
  assert.ok(page.includes('providerLabel(event.providerName)'));
  assert.ok(page.includes('formatCarePilotDateTime(row.queuedAt, clinicTimeZone)'));
  assert.ok(page.includes('formatCarePilotDateTime(timeline.execution.scheduledAt, clinicTimeZone)'));
  assert.ok(page.includes('formatCarePilotDateTime(timeline.execution.createdAt, clinicTimeZone)'));
  assert.ok(page.includes('formatCarePilotDateTime(timeline.execution.executedAt, clinicTimeZone)'));
  assert.ok(page.includes('Execution Status:'));
  assert.ok(page.includes('Delivery Status:'));
  assert.ok(page.includes('Queue Age'));
  assert.ok(page.includes('Queue Wait:'));
  assert.ok(page.includes('Processing Duration:'));
  assert.ok(page.includes('humanizeCarePilotCode(row.status)'));
  assert.ok(page.includes('humanizeCarePilotCode(row.deliveryStatus)'));
  assert.ok(page.includes('humanizeCarePilotCode(timeline.execution.status)'));
  assert.ok(page.includes('humanizeCarePilotCode(timeline.execution.deliveryStatus)'));
  assert.ok(page.includes('humanizeCarePilotCode(attempt.deliveryStatus)'));
  assert.ok(page.includes('humanizeCarePilotCode(event.internalStatus)'));
  assert.ok(page.includes('formatCarePilotDateTime(attempt.attemptedAt, clinicTimeZone)'));
  assert.ok(page.includes('formatCarePilotDateTime(event.eventTimestamp, clinicTimeZone)'));
  assert.ok(page.includes('aria-label="Close execution timeline"'));
  assert.ok(page.includes('Close execution timeline'));
  assert.ok(page.includes('onClose={closeTimeline}'));
  assert.ok(page.includes('viewAttemptsButtonRef.current?.focus()'));
  assert.ok(page.includes('formatCarePilotDurationMinutes(queueWaitMinutes(timeline.execution))'));
  assert.ok(page.includes('formatCarePilotDurationMinutes(processingDurationMinutes(timeline.execution))'));
  assert.ok(page.includes('DialogActions'));
  assert.ok(page.includes('Close</Button>'));
  assert.ok(page.includes('retryCount'));
  assert.ok(!page.includes('processingDurationMinutes(timeline.execution.createdAt, timeline.execution.executedAt)'));
  assert.ok(!page.includes("alert("));
  assert.ok(!page.includes("prompt("));
  assert.ok(!page.includes("confirm("));

  assert.ok(formatting.includes('"carepilot email smtp": "Email SMTP"'));
  assert.ok(formatting.includes('IST ('));
});
