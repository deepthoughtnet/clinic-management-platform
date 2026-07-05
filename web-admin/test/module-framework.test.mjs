import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("module registry declares laboratory and reports modules and landing rules", () => {
  const source = readSource("modules/moduleRegistry.ts");
  assert.match(source, /"LABORATORY"/);
  assert.match(source, /"REPORTS"/);
  assert.match(source, /"PHARMACY_POS"/);
  assert.match(source, /"pharmacy-procurement"/);
  assert.match(source, /"pharmacy-reconciliation"/);
  assert.match(source, /"pharmacy-pos": \{ moduleAny: \["PHARMACY_POS"\] \}/);
  assert.match(source, /export function isPharmacyWorkspaceRole/);
  assert.match(source, /export function isLabWorkspaceRole/);
  assert.match(source, /export function isBillingWorkspaceRole/);
  assert.match(source, /path === "\/pharmacy\/dashboard"/);
  assert.match(source, /path === "\/lab" \|\| path === "\/laboratory"/);
  assert.match(source, /path === "\/billing"/);
  assert.match(source, /LAB_FRONT_DESK/);
  assert.match(source, /LAB_TECHNICIAN/);
  assert.match(source, /LAB_APPROVER/);
});

test("app routes use module-aware landing and feature gates", () => {
  const source = readSource("app/App.tsx");
  assert.match(source, /resolveTenantLandingPage\(auth\)/);
  assert.match(source, /function RouteAccessGate/);
  assert.match(source, /function FeatureGate/);
  assert.match(source, /path="\/dashboard".*featureId="clinic-dashboard"/s);
  assert.match(source, /path="\/pharmacy\/dashboard".*RouteAccessGate.*featureId="pharmacy-dashboard"/s);
  assert.match(source, /path="\/pharmacy\/procurement".*RouteAccessGate.*featureId="pharmacy-procurement"/s);
  assert.match(source, /path="\/pharmacy\/reconciliation".*RouteAccessGate.*featureId="pharmacy-reconciliation"/s);
  assert.match(source, /path="\/lab".*RouteAccessGate.*featureId="laboratory"/s);
  assert.match(source, /path="\/laboratory".*Navigate to="\/lab"/s);
  assert.match(source, /path="\/reports".*featureId="reports"/s);
});

test("sidebar filters tenant navigation by module entitlements", () => {
  const source = readSource("layout/SidebarNav.tsx");
  assert.match(source, /resolveEnabledTenantModules\(auth\)/);
  assert.match(source, /canAccessFeature\(auth, "pharmacy-pos"\)/);
  assert.match(source, /isRouteAccessibleForAuth\(auth, item\.path\)/);
  assert.match(source, /item\.moduleAll && !item\.moduleAll\.every/);
  assert.match(source, /item\.moduleAny && !item\.moduleAny\.some/);
});

test("dashboard page hides financial widgets from receptionist role", () => {
  const source = readSource("pages/DashboardPage.tsx");
  assert.match(source, /const canSeeFinancialDashboard = isBillingUser \|\| isClinicAdmin \|\| isAuditor \|\| isPlatformAdmin;/);
  assert.match(source, /const showBilling = Boolean\(billing\) && canSeeFinancialDashboard;/);
  assert.match(source, /const operationalCards = dashboard \? \[/);
  assert.match(source, /const financialCards = dashboard \? \[/);
  assert.match(source, /isBillingUser \? "Finance Snapshot" : "Billing Snapshot"/);
});

test("tenant admin pages expose laboratory and reports module toggles", () => {
  const tenantsPage = readSource("pages/platform/TenantsPage.tsx");
  const tenantDetailPage = readSource("pages/platform/TenantDetailPage.tsx");
  assert.match(tenantsPage, /"LABORATORY"/);
  assert.match(tenantsPage, /"REPORTS"/);
  assert.match(tenantDetailPage, /"LABORATORY"/);
  assert.match(tenantDetailPage, /"REPORTS"/);
});
