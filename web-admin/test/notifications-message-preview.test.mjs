import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("notifications page clamps message preview to two lines and exposes the full message on hover", () => {
  const source = readSource("pages/notifications/NotificationsPage.tsx");

  assert.ok(source.includes("Message preview"));
  assert.ok(source.includes("Tooltip title={row.message}"));
  assert.ok(source.includes("WebkitLineClamp: 2"));
  assert.ok(source.includes("whiteSpace: \"normal\""));
  assert.ok(source.includes("maxWidth: 420"));
  assert.ok(source.includes("maxHeight: \"calc(2 * 1.35em)\""));
});

test("notifications page groups logical notifications and shows channel details in a stable order", () => {
  const source = readSource("pages/notifications/NotificationsPage.tsx");

  assert.ok(source.includes("getGroupedNotifications"));
  assert.ok(source.includes("logicalNotificationId"));
  assert.ok(source.includes("Channels"));
  assert.ok(source.includes("Overall"));
  assert.ok(source.includes("Collapse"));
  assert.ok(source.includes("KeyboardArrowDown"));
  assert.ok(source.includes("KeyboardArrowUp"));
  assert.ok(source.includes('CHANNEL_ORDER: NotificationChannel[] = ["IN_APP", "EMAIL", "SMS", "WHATSAPP"]'));
  assert.ok(source.includes("normalizeNotificationChannel(item.channel) === channel"));
  assert.ok(source.includes("function normalizeNotificationReason(channel: NotificationChannel, reason: string | null)"));
  assert.ok(source.includes("channelPresentation(delivery)"));
  assert.ok(source.includes('return "IN_APP"'));
  assert.ok(source.includes('return "EMAIL"'));
  assert.ok(source.includes('return "SMS"'));
  assert.ok(source.includes('return "WHATSAPP"'));
  assert.ok(source.includes('Display status: {delivery ? channelPresentation(delivery).statusLabel : "Not enabled"}'));
  assert.ok(source.includes('normalizeNotificationReason(entry.channel, delivery.failureReason)'));
  assert.ok(source.includes('key={entry.channel}'));
  assert.ok(source.includes('whiteSpace: "nowrap"'));
  assert.ok(source.includes('rowGap: 0.75'));
  assert.ok(source.includes('return "In-App"'));
  assert.ok(source.includes('return "Email"'));
  assert.ok(source.includes('return "SMS"'));
  assert.ok(source.includes('return "WhatsApp"'));
  assert.ok(source.includes('title = `${entry.label}: ${entry.title}`'));
  assert.ok(source.includes('notifications disabled'));
});
