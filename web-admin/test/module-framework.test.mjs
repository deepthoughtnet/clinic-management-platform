import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("module registry declares laboratory and reports modules and landing rules", () => {
  const source = readSource("modules/moduleRegistry.ts");
  assert.ok(source.includes('"LABORATORY"'));
  assert.ok(source.includes('"REPORTS"'));
  assert.ok(source.includes('"PHARMACY_POS"'));
  assert.ok(source.includes('"pharmacy-procurement"'));
  assert.ok(source.includes('"pharmacy-reconciliation"'));
  assert.ok(source.includes('"pharmacy-pos": { moduleAny: ["PHARMACY_POS"] }'));
  assert.ok(source.includes("export function isPharmacyWorkspaceRole"));
  assert.ok(source.includes("export function isLabWorkspaceRole"));
  assert.ok(source.includes("export function isBillingWorkspaceRole"));
  assert.ok(source.includes("lab.reception.access"));
  assert.ok(source.includes('path === "/pharmacy/dashboard"'));
  assert.ok(source.includes('path === "/lab" || path === "/laboratory"'));
  assert.ok(source.includes('path === "/billing"'));
  assert.ok(source.includes("LAB_FRONT_DESK"));
  assert.ok(source.includes("LAB_TECHNICIAN"));
  assert.ok(source.includes("LAB_APPROVER"));
});

test("app routes use module-aware landing and feature gates", () => {
  const source = readSource("app/App.tsx");
  assert.ok(source.includes("resolveTenantLandingPage(auth)"));
  assert.ok(source.includes("function RouteAccessGate"));
  assert.ok(source.includes("function FeatureGate"));
  assert.ok(source.includes('path="/dashboard"') && source.includes('featureId="clinic-dashboard"'));
  assert.ok(source.includes('path="/pharmacy/dashboard"') && source.includes('featureId="pharmacy-dashboard"'));
  assert.ok(source.includes('path="/pharmacy/procurement"') && source.includes('featureId="pharmacy-procurement"'));
  assert.ok(source.includes('path="/pharmacy/reconciliation"') && source.includes('featureId="pharmacy-reconciliation"'));
  assert.ok(source.includes('path="/lab"') && source.includes('featureId="laboratory"'));
  assert.ok(source.includes('path="/laboratory"') && source.includes('Navigate to="/lab"'));
  assert.ok(source.includes('path="/reports"') && source.includes('featureId="reports"'));
});

test("sidebar filters tenant navigation by module entitlements", () => {
  const source = readSource("layout/SidebarNav.tsx");
  assert.ok(source.includes("resolveEnabledTenantModules(auth)"));
  assert.ok(source.includes('canAccessFeature(auth, "pharmacy-pos")'));
  assert.ok(source.includes("isRouteAccessibleForAuth(auth, item.path)"));
  assert.ok(source.includes("item.moduleAll && !item.moduleAll.every"));
  assert.ok(source.includes("item.moduleAny && !item.moduleAny.some"));
});

test("dashboard page hides financial widgets from receptionist role", () => {
  const source = readSource("pages/DashboardPage.tsx");
  assert.ok(source.includes("const canSeeFinancialDashboard = isBillingUser || isClinicAdmin || isAuditor || isPlatformAdmin;"));
  assert.ok(source.includes("const showBilling = Boolean(billing) && canSeeFinancialDashboard;"));
  assert.ok(source.includes("const operationalCards = dashboard ? ["));
  assert.ok(source.includes("const financialCards = dashboard ? ["));
  assert.ok(source.includes('isBillingUser ? "Finance Snapshot" : "Billing Snapshot"'));
});

test("tenant admin pages expose laboratory and reports module toggles", () => {
  const tenantsPage = readSource("pages/platform/TenantsPage.tsx");
  const tenantDetailPage = readSource("pages/platform/TenantDetailPage.tsx");
  assert.ok(tenantsPage.includes('"LABORATORY"'));
  assert.ok(tenantsPage.includes('"REPORTS"'));
  assert.ok(tenantDetailPage.includes('"LABORATORY"'));
  assert.ok(tenantDetailPage.includes('"REPORTS"'));
});
