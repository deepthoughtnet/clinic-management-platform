import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("lead page uses shared assignee labels and tenant timezone formatting", () => {
  const page = readSource("products/carepilot/leads/LeadsPage.tsx");
  const formatting = readSource("products/carepilot/shared/carepilotFormatting.ts");
  const api = readSource("api/clinicApi.ts");

  assert.ok(page.includes('useCarePilotTenantTimezone'));
  assert.ok(page.includes('formatLeadDateTime'));
  assert.ok(page.includes('formatCarePilotDateTime(value, clinicTimeZone)'));
  assert.ok(page.includes('formatCarePilotAssigneeLabel(users.find((u) => u.appUserId === id), id)'));
  assert.ok(page.includes('formatCarePilotAssigneeLabel(u, u.appUserId)'));
  assert.ok(page.includes('const pipelineOnly = tab === "PIPELINE";'));
  assert.ok(page.includes('listCarePilotLeads(auth.accessToken, auth.tenantId, { status: forcedStatus, source: parsed.data.source || undefined, priority: parsed.data.priority || undefined, search: parsed.data.search || undefined, followUpDue, pipelineOnly, page: requestPage, size: requestSize })'));
  assert.ok(page.includes('No leads found for current filters.'));
  assert.ok(!page.includes('Unknown user'));
  assert.ok(formatting.includes('return "Unassigned";'));
  assert.ok(formatting.includes('return "Unavailable user";'));
  assert.ok(formatting.includes('formatCarePilotDateTime'));
  assert.ok(api.includes('pipelineOnly?: boolean;'));
  assert.ok(api.includes('query.set("pipelineOnly", "true")'));
});
