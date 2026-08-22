import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("ai ops page labels the summary and log scopes distinctly", () => {
  const source = readSource("pages/admin/AiOpsPage.tsx");

  assert.ok(source.includes("Usage summary reflects the last 30 days."));
  assert.ok(source.includes("Invocation logs show the latest 20 tenant-scoped records."));
  assert.ok(source.includes("Invocation Logs (Recent 20)"));
  assert.ok(source.includes("Showing the latest 20 records from the tenant-wide invocation feed."));
});

test("ai ops page treats unavailable telemetry as N/A instead of a zero default", () => {
  const source = readSource("pages/admin/AiOpsPage.tsx");
  const api = readSource("api/clinicApi.ts");

  assert.ok(source.includes("outputTokenTelemetryAvailable ? usage.outputTokens : \"N/A\""));
  assert.ok(source.includes("estimatedCostTelemetryAvailable ? usage.estimatedCost.toFixed(4) : \"N/A\""));
  assert.ok(api.includes("outputTokenTelemetryAvailable: boolean"));
  assert.ok(api.includes("estimatedCostTelemetryAvailable: boolean"));
});

