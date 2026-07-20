import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("ops console separates draft and applied filters and uses business references", () => {
  const page = readSource("products/carepilot/ops/OpsConsolePage.tsx");
  const filters = readSource("products/carepilot/ops/opsConsoleFilters.ts");
  const selector = readSource("products/carepilot/components/CampaignLookupField.tsx");

  assert.ok(page.includes("draftFilters"));
  assert.ok(page.includes("appliedFilters"));
  assert.ok(page.includes("parseOpsFilters"));
  assert.ok(page.includes("serializeOpsFilters"));
  assert.ok(page.includes('type="submit"'));
  assert.ok(page.includes('type="button"'));
  assert.ok(page.includes('onSelectOption'));
  assert.ok(page.includes("executionReference"));
  assert.ok(page.includes("executionId"));
  assert.ok(page.includes("Queued / Stuck"));
  assert.ok(page.includes("Failed / Retrying"));
  assert.ok(page.includes("Queue Readiness"));
  assert.ok(page.includes("manualExecutionDispatcherEnabled"));
  assert.ok(page.includes("Manual execution dispatcher"));
  assert.ok(page.includes("providerStatuses"));
  assert.ok(page.includes("CampaignLookupField"));
  assert.ok(page.includes("Selected campaign:"));
  assert.ok(!page.includes("void load()"));

  assert.ok(filters.includes("parseOpsFilters"));
  assert.ok(filters.includes("serializeOpsFilters"));
  assert.ok(filters.includes("OPS_QUEUED_STATUSES"));
  assert.ok(filters.includes("OPS_FAILED_STATUSES"));
  assert.ok(filters.includes("campaignRef"));
  assert.ok(filters.includes("retryableOnly"));

  assert.ok(selector.includes("selectedOption"));
  assert.ok(selector.includes("onSelectOption"));
  assert.ok(selector.includes("reason === \"input\""));
});
