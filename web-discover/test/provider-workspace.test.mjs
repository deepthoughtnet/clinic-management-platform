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

  assert.ok(routes.includes('providerWorkspace: { path: "/provider"'));
  assert.ok(routes.includes('providerApplications: { path: "/provider/applications"'));
  assert.ok(routes.includes('providerApplicationDashboard: { path: "/provider/applications/:applicationReference"'));
  assert.ok(routes.includes('providerLandingPage: { path: "/provider/profiles"'));
  assert.ok(routes.includes('providerAccount: { path: "/provider/account"'));
  assert.ok(app.includes('path="/provider/workspace" element={<Navigate to={DISCOVER_ROUTES.providerWorkspace.path} replace />}'));
  assert.ok(app.includes('path="/provider/dashboard" element={<Navigate to={DISCOVER_ROUTES.providerWorkspace.path} replace />}'));
  assert.ok(app.includes('path="/provider/landing-page" element={<Navigate to={DISCOVER_ROUTES.providerLandingPage.path} replace />}'));
  assert.ok(page.includes("Manage your applications and public profiles"));
  assert.ok(page.includes("Account &amp; Security"));
  assert.ok(page.includes("Recent Activity"));
  assert.ok(page.includes("Signed in with"));
  assert.ok(page.includes("Logout"));
  assert.ok(page.includes("Switch account"));
  assert.ok(!page.includes("Provider account ID:"));
  assert.ok(!page.includes("HttpOnly cookie"));
});

test("provider workspace continue-registration links use exact application references", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");

  assert.ok(page.includes('DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber))'));
  assert.ok(!page.includes("DISCOVER_ROUTES.providerDashboard.path"));
});
