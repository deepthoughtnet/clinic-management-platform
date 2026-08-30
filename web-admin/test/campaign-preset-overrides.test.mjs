import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("campaign preset selection only fills untouched trigger and audience fields", () => {
  const page = readSource("products/carepilot/campaigns/CampaignsPage.tsx");

  assert.ok(page.includes('campaignType: c.campaignType === defaults.campaignType ? preset.campaignType : c.campaignType'));
  assert.ok(page.includes('triggerType: c.triggerType === defaults.triggerType ? preset.triggerType : c.triggerType'));
  assert.ok(page.includes('audienceType: c.audienceType === defaults.audienceType ? preset.audienceType : c.audienceType'));
  assert.ok(page.includes("Preset defaults only replace trigger/audience values that are still untouched."));
});
