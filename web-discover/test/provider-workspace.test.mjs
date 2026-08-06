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
  assert.ok(app.includes('path="/provider/workspace" element={<LegacyRedirect to={DISCOVER_ROUTES.providerWorkspace.path} />}'));
  assert.ok(app.includes('path="/provider/dashboard" element={<LegacyRedirect to={DISCOVER_ROUTES.providerWorkspace.path} />}'));
  assert.ok(app.includes('path="/provider/landing-page" element={<LegacyRedirect to={DISCOVER_ROUTES.providerLandingPage.path} />}'));
  assert.ok(app.includes("function LegacyRedirect({ to }: { to: string })"));
  assert.ok(app.includes("return <Navigate replace to={`${to}${location.search}`} />;"));
  assert.ok(app.includes('<Route element={<ProviderProtectedRoute />}>'));
  assert.ok(page.includes("Manage your provider profiles."));
  assert.ok(page.includes("Create another profile"));
  assert.ok(page.includes("Continue Profile"));
  assert.ok(page.includes("Submit for Platform Review"));
  assert.ok(page.includes("Review Changes"));
  assert.ok(page.includes("View Published Profile"));
  assert.ok(page.includes("My Provider Profiles"));
  assert.ok(page.includes("Ownership: "));
  assert.ok(page.includes("Publication: "));
  assert.ok(page.includes("Connection: "));
  assert.ok(page.includes("Active Profiles"));
  assert.ok(page.includes("Ready for Review"));
  assert.ok(page.includes("Under Platform Review"));
  assert.ok(page.includes("Published"));
  assert.ok(page.includes("Needs Attention"));
  assert.ok(page.includes("No actions currently require your attention."));
  assert.ok(page.includes("Recent activity"));
  assert.ok(page.includes("Active session"));
  assert.ok(page.includes("Logout"));
  assert.ok(page.includes("Switch account"));
  assert.ok(page.includes("workItems"));
  assert.ok(page.includes("providerProfiles"));
  assert.ok(page.includes("profile.lifecycleLabel"));
  assert.ok(page.includes("activeProfileCount"));
  assert.ok(page.includes("readyForReviewCount"));
  assert.ok(page.includes("underReviewCount"));
  assert.ok(page.includes("publishedCount"));
  assert.ok(page.includes("needsAttentionCount"));
  assert.ok(!page.includes("Provider account ID:"));
  assert.ok(!page.includes("HttpOnly cookie"));
  assert.ok(!page.includes("workspace?.activeProfileCount ?? providerProfiles.length"));
  assert.ok(!page.includes("statusLabel(application.status)"));
  assert.ok(page.includes("supportedProviderTypes"));
  assert.ok(styles.includes(".provider-account-header"));
  assert.ok(styles.includes(".provider-account-summary-grid"));
  assert.ok(styles.includes(".provider-account-application-grid"));
  assert.ok(styles.includes(".provider-account-summary-card"));
  assert.ok(styles.includes(".provider-account-attention-copy"));
  assert.ok(styles.includes(".provider-account-attention-meta"));
});

test("provider workspace continue-registration links use exact application references", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");

  assert.ok(page.includes("DISCOVER_ROUTES.providerPublicProfileDraft.path"));
  assert.ok(page.includes('replace(":profileReference", encodeURIComponent(profile.publicProfileReference))'));
  assert.ok(page.includes('replace(":section", "overview")'));
  assert.ok(page.includes("Create another profile"));
  assert.ok(!page.includes("DISCOVER_ROUTES.providerDashboard.path"));
});
