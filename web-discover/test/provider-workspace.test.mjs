import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider workspace uses canonical provider routes and business-facing account text", () => {
  const routes = read("src/routes.ts");
  const app = read("src/App.tsx");
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  const styles = read("src/styles.css");

  assert.ok(routes.includes('providerWorkspace: { path: "/provider"'));
  assert.ok(routes.includes('providerApplications: { path: "/provider/applications"'));
  assert.ok(routes.includes('providerApplicationDashboard: { path: "/provider/applications/:applicationReference"'));
  assert.ok(routes.includes('providerLandingPage: { path: "/provider/profiles"'));
  assert.ok(routes.includes('providerAccount: { path: "/provider/account"'));
  assert.ok(app.includes('path="/provider/workspace" element={<Navigate to={DISCOVER_ROUTES.providerWorkspace.path} replace />}'));
  assert.ok(app.includes('path="/provider/dashboard" element={<Navigate to={DISCOVER_ROUTES.providerWorkspace.path} replace />}'));
  assert.ok(app.includes('path="/provider/landing-page" element={<Navigate to={DISCOVER_ROUTES.providerLandingPage.path} replace />}'));
  assert.ok(app.includes('<Route element={<ProviderProtectedRoute />}>'));
  assert.ok(page.includes("Manage your applications and published profiles."));
  assert.ok(page.includes("Add another profile"));
  assert.ok(page.includes("Continue registration"));
  assert.ok(page.includes("Active applications"));
  assert.ok(page.includes("Published profiles"));
  assert.ok(page.includes("Items needing attention"));
  assert.ok(page.includes("No actions currently require your attention."));
  assert.ok(page.includes("Current step:"));
  assert.ok(page.includes("My applications"));
  assert.ok(page.includes("My public profiles"));
  assert.ok(page.includes("Recent activity"));
  assert.ok(page.includes("Active session"));
  assert.ok(page.includes("Logout"));
  assert.ok(page.includes("Switch account"));
  assert.ok(!page.includes("Provider account ID:"));
  assert.ok(!page.includes("HttpOnly cookie"));
  assert.ok(page.includes("requiresAttention"));
  assert.ok(page.includes("supportedProviderTypes"));
  assert.ok(styles.includes(".provider-account-header"));
  assert.ok(styles.includes(".provider-account-summary-grid"));
  assert.ok(styles.includes(".provider-account-application-grid"));
  assert.ok(styles.includes(".provider-account-two-column"));
});

test("provider workspace continue-registration links use exact application references", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");

  assert.ok(page.includes('createProviderOnboardingAccess(application.referenceNumber)'));
  assert.ok(page.includes('providerOnboardingStepRoute(application.currentStep)'));
  assert.ok(page.includes('DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber))'));
  assert.ok(page.includes("Add another profile"));
  assert.ok(!page.includes("DISCOVER_ROUTES.providerDashboard.path"));
});
