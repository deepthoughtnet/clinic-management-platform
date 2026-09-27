import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("communication test is a platform-only provider console", () => {
  const page = readSource("pages/platform/CommunicationTestPage.tsx");
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const api = readSource("api/clinicApi.ts");

  assert.ok(app.includes("/platform/communication-test"));
  assert.ok(nav.includes("Communication Test"));
  assert.ok(page.includes("Provider test"));
  assert.ok(page.includes('label="Provider"'));
  assert.ok(api.includes("provider?: string"));
  assert.ok(page.includes("No appointment, prescription, lab, pharmacy, campaign, or reminder state is changed."));
  assert.ok(api.includes("getCommunicationTestHealth"));
  assert.ok(api.includes("runCommunicationTest"));
  assert.ok(page.includes('selectedHealth.status === "NOT_CONFIGURED"'));
  assert.ok(page.includes('selectedHealth.status === "ERROR"'));
  assert.ok(page.includes("emailRecipient"));
  assert.ok(page.includes("phoneRecipient"));
  assert.ok(page.includes('channel === "VOICE"'));
  assert.ok(!page.includes("apiKey"));
  assert.ok(!page.includes("password"));
});
