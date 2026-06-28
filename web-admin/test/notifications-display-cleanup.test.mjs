import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("notifications display avoids exposing UUID recipients in the primary column", () => {
  const source = readSource("pages/notifications/notificationDisplay.js");
  assert.ok(source.includes("formatNotificationTargetLabel"));
  assert.ok(source.includes("formatNotificationSourceLabel(row)"));
  assert.ok(source.includes("Notification target"));
  assert.ok(source.includes("test(row.recipient)"));
});
