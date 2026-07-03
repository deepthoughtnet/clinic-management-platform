import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("lab page gates actions with explicit lab permissions", () => {
  const source = readSource("pages/lab/LabPage.tsx");

  assert.ok(source.includes('const canManageTests = auth.hasPermission("lab.test.manage");'));
  assert.ok(source.includes('const canCollectPayment = auth.hasPermission("lab.order.collect_payment");'));
  assert.ok(source.includes('const canCollectSample = auth.hasPermission("lab.order.collect_sample");'));
  assert.ok(source.includes('const canEnterResults = auth.hasPermission("lab.order.result_entry");'));
  assert.ok(source.includes('const canGenerateReport = auth.hasPermission("lab.order.generate_report");'));
  assert.ok(source.includes('const canReviewReport = auth.hasPermission("lab.order.review");'));
  assert.ok(source.includes('const canCreateOrders = auth.hasPermission("lab.order.create");'));
  assert.ok(source.includes('const canQuickRegisterPatient = canCreateOrders && auth.hasPermission("patient.create") && auth.hasPermission("patient.read");'));
  assert.ok(!source.includes('auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN")'));
});

test("lab csv template download requests csv text and not json", () => {
  const apiSource = fs.readFileSync(path.join(process.cwd(), "web-admin", "src", "api", "clinicApi.ts"), "utf8");
  assert.ok(apiSource.includes('return httpGetText("/api/lab/tests/import-template", { token, tenantId, accept: "text/csv, */*" });'));
});

test("lab ordering hides disabled tests and admin configuration stays hidden from lab roles", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('const availableTests = React.useMemo('));
  assert.ok(source.includes('row.active && row.enabled && activeCategoryCodes.has(row.category)'));
  assert.ok(source.includes('{canManageTests ? <Tab label="Lab Configuration" /> : null}'));
  assert.ok(source.includes('<LabAnalyticsPanel data={dashboardData.analytics} defaultExpanded={analyticsExpandedByDefault} onAction={handleDashboardAction} />'));
  assert.ok(source.includes('const visibleCatalogTests = React.useMemo(() => filteredTests.slice(0, 50), [filteredTests]);'));
  assert.ok(source.includes('Showing first 50 tests. Use search to find more.'));
  assert.ok(source.includes('stickyHeader'));
});

test("lab dashboard prioritizes quick actions and my work before analytics", () => {
  const source = readSource("pages/lab/LabDashboard.tsx");
  assert.ok(source.includes('title="Quick Actions"'));
  assert.ok(source.includes('My Work Today'));
  assert.ok(source.includes('pending items'));
  assert.ok(source.includes('Workflow guide'));
  assert.ok(source.includes('Dashboard Analytics'));
});
