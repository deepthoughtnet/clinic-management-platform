import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const api = fs.readFileSync(new URL("../src/api/clinicApi.ts", import.meta.url), "utf8");
const panel = fs.readFileSync(new URL("../src/pages/platform/CommunicationChannelsPanel.tsx", import.meta.url), "utf8");
const detail = fs.readFileSync(new URL("../src/pages/platform/TenantDetailPage.tsx", import.meta.url), "utf8");

test("platform communication channel API and tenant detail panel are wired", () => {
  assert.match(api, /getPlatformCommunicationChannels/);
  assert.match(api, /createPlatformCommunicationChannel/);
  assert.match(api, /updatePlatformCommunicationChannel/);
  assert.match(api, /setPlatformCommunicationChannelEnabled/);
  assert.match(panel, /Communication Channels/);
  assert.match(panel, /Default outbound/);
  assert.match(panel, /Location ID \(optional\)/);
  assert.match(panel, /clinicId: null, locationId: null/);
  assert.match(panel, /row\.normalizedAddress.*Entered as \{row\.address\}/);
  assert.match(panel, /row\.locationId \? `Location/);
  assert.match(panel, /row\.clinicId \? "Clinic" : "Tenant"/);
  assert.match(panel, /const resetToCreate = React\.useCallback/);
  assert.match(panel, /priority: 0/);
  assert.match(panel, /<Button onClick=\{resetToCreate\}>Cancel<\/Button>/);
  assert.match(panel, /resetToCreate\(\); await load\(\);/);
  assert.match(panel, /await setPlatformCommunicationChannelEnabled[\s\S]*?resetToCreate\(\); await load\(\);/);
  assert.match(detail, /CommunicationChannelsPanel/);
  assert.doesNotMatch(detail, /CommunicationChannelsPanel tenantId=\{tenant\.tenant\.id\} clinicId=/);
});
