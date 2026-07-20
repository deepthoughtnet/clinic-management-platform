import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("engage routes enforce explicit permission gates and user-facing engage labels", () => {
  const appSource = readSource("app/App.tsx");
  const navSource = readSource("layout/nav.ts");
  const modulesSource = readSource("modules/moduleRegistry.ts");
  const aiOpsSource = readSource("products/carepilot/ai-operations/AiOperationsPage.tsx");
  const analyticsSource = readSource("products/carepilot/analytics/AnalyticsPage.tsx");
  const engagementSource = readSource("products/carepilot/engagement/PatientEngagementPage.tsx");

  assert.ok(appSource.includes("function PermissionGate"));
  assert.ok(appSource.includes('path="/carepilot/ai-operations"'));
  assert.ok(appSource.includes('Navigate to="/carepilot/ai-operations?tab=calls"'));
  assert.ok(appSource.includes('Navigate to="/carepilot/ai-operations?tab=work-queue&type=callback"'));
  assert.ok(appSource.includes('anyPermissions={["engage.campaign.view", "engage.audit.view"]}'));
  assert.ok(appSource.includes('anyPermissions={[ENGAGE_ANALYTICS_VIEW]}'));
  assert.ok(!appSource.includes('engage.campaign.manage", "engage.audit.view'));
  assert.ok(!appSource.includes('engage.campaign.activate'));
  assert.ok(aiOpsSource.includes('canViewCalls ? shortcutButton("Open Calls"'));
  assert.ok(aiOpsSource.includes('canViewCalls ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("AI Calls"'));
  assert.ok(navSource.includes('label: "ENGAGE"'));
  assert.ok(navSource.includes('key: "ai-operations"'));
  assert.ok(navSource.includes('label: "AI Operations"'));
  assert.ok(navSource.includes('permissionsAny: [ENGAGE_ANALYTICS_VIEW]'));
  assert.ok(!navSource.includes('{ key: "analytics", label: "Analytics", path: "/carepilot/analytics", requiresTenant: true, rolesAny:'));
  assert.ok(modulesSource.includes('displayName: "Engage"'));
  assert.ok(modulesSource.includes('"/carepilot/ai-operations"'));
  assert.ok(analyticsSource.includes("ENGAGE_ANALYTICS_VIEW"));
  assert.ok(engagementSource.includes("ENGAGE_ANALYTICS_VIEW"));
});
