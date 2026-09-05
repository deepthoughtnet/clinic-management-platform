import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "src");

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("platform ops page keeps the existing control center and adds the new overview matrix", () => {
  const page = read("pages/admin/PlatformOpsPage.tsx");
  const api = read("api/clinicApi.ts");

  assert.ok(page.includes("Promise.allSettled"));
  assert.ok(page.includes("Production Health Matrix"));
  assert.ok(page.includes("Refresh"));
  assert.ok(page.includes("Test Now"));
  assert.ok(page.includes("Test Groq Now"));
  assert.ok(page.includes("Test again in"));
  assert.ok(page.includes("Open AI Ops"));
  assert.ok(page.includes("Open AI Reasoning Console"));
  assert.ok(page.includes("Alert Rules"));
  assert.ok(page.includes("Active Alerts"));
  assert.ok(page.includes("Provider Health"));
  assert.ok(page.includes("Queue Health"));
  assert.ok(page.includes("DLQ Monitoring"));
  assert.ok(page.includes("Tenant-specific operational details are hidden in Platform mode."));
  assert.ok(page.includes("Hidden in Platform mode."));
  assert.ok(page.includes("Structured backup telemetry not available"));
  assert.ok(page.includes("Telemetry not available"));
  assert.ok(page.includes("AI Activity - Last 24 Hours"));

  assert.ok(api.includes("getPlatformOperationsOverview"));
  assert.ok(api.includes("testPlatformOperationComponent"));
  assert.ok(api.includes("PlatformOperationsOverview"));
  assert.ok(api.includes("PlatformOperationsAiSummary"));
  assert.ok(api.includes("PlatformOperationsComponentHealth"));
  assert.ok(api.includes('"/api/platform/operations/overview"'));
  assert.ok(api.includes("`/api/platform/operations/health/${component}/test`"));
});
