import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(new URL("..", import.meta.url).pathname, "src");
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");

test("Engage exposes Voice as a disabled-by-default configuration channel", () => {
  const settings = read("pages/admin/NotificationSettingsPage.tsx");
  const model = read("pages/admin/notificationSettingsModel.ts");
  const api = read("api/clinicApi.ts");
  assert.match(settings, /Voice enabled/);
  assert.match(settings, /Engage execution is globally disabled/);
  assert.match(api, /voiceExecutionEnabled/);
  assert.match(model, /\[\"IN_APP\", \"EMAIL\", \"SMS\", \"WHATSAPP\", \"VOICE\"\]/);
  assert.match(model, /VOICE: Boolean\(value\.VOICE/);
});

test("Campaign, reminder and operations views include Voice without enabling execution", () => {
  const campaigns = read("products/carepilot/campaigns/CampaignsPage.tsx");
  const reminders = read("products/carepilot/reminders/RemindersPage.tsx");
  const ops = read("products/carepilot/ops/OpsConsolePage.tsx");
  assert.match(campaigns, /\"VOICE\"/);
  assert.match(campaigns, /Execution disabled globally/);
  assert.match(reminders, /value=\"VOICE\"/);
  assert.match(ops, /value=\"VOICE\"/);
});
