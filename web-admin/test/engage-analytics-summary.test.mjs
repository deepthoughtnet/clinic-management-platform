import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("engage analytics renders delivery buckets and business references", () => {
  const source = readSource("products/carepilot/analytics/AnalyticsPage.tsx");
  const shellSource = readSource("layout/AppShell.tsx");
  const apiSource = readSource("api/clinicApi.ts");

  assert.ok(source.includes('kpi("Queued", summary.queuedExecutions)'));
  assert.ok(source.includes('kpi("Sent", summary.sentExecutions)'));
  assert.ok(source.includes('kpi("Delivered", summary.deliveredExecutions)'));
  assert.ok(source.includes('kpi("Read", summary.readExecutions)'));
  assert.ok(source.includes('kpi("Undelivered", summary.undeliveredExecutions)'));
  assert.ok(source.includes('executionStatusLabel(key)') || source.includes("executionStatusLabel(key as"));
  assert.ok(source.includes('channelTypeLabel(key as CarePilotChannelType)'));
  assert.ok(source.includes('`${row.campaignName} · ${row.campaignReference}`'));
  assert.ok(source.includes('Jeevanam Engage Analytics'));
  assert.ok(shellSource.includes('useNavigationType'));
  assert.ok(shellSource.includes('window.scrollTo({ left: 0, top: 0, behavior: "auto" })'));
  assert.ok(shellSource.includes('scrollPositionsRef.current.set(location.key'));
  assert.ok(apiSource.includes("queuedExecutions: number;"));
  assert.ok(apiSource.includes("sentExecutions: number;"));
  assert.ok(apiSource.includes("campaignReference: string;"));
  assert.ok(!source.includes("bouncedExecutions"));
});
