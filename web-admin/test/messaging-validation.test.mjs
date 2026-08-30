import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("messaging test-send keeps invalid submissions disabled and hides raw config keys", () => {
  const page = readSource("products/carepilot/messaging/MessagingPage.tsx");

  assert.ok(page.includes("testSendValidation"));
  assert.ok(page.includes("!testSendValidation.valid"));
  assert.ok(page.includes("validateRecipient"));
  assert.ok(page.includes("Configuration guidance"));
  assert.ok(page.includes("Use the provider status above to confirm whether EMAIL is READY."));
  assert.ok(page.includes("If EMAIL is not READY, finish tenant messaging setup before testing."));
  assert.ok(page.includes("Use the provider status above to confirm whether SMS is READY."));
  assert.ok(page.includes("Use the provider status above to confirm whether WhatsApp is READY."));
  assert.ok(!page.includes("CLINIC_ENGAGE_MESSAGING_"));
  assert.ok(!page.includes("provider property"));
});
