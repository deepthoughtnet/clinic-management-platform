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
  assert.ok(page.includes('workspace?.applications ?? []'));
  assert.ok(page.includes('workspace?.publishedProfiles ?? []'));
  assert.ok(page.includes("applicationCards"));
  assert.ok(page.includes("Resume draft"));
  assert.ok(page.includes("Continue setup"));
  assert.ok(page.includes("Submit for review"));
  assert.ok(page.includes("View status"));
  assert.ok(page.includes("Review changes"));
  assert.ok(page.includes("View profile"));
  assert.ok(page.includes("My Provider Profiles"));
  assert.ok(page.includes("Ownership: "));
  assert.ok(page.includes("Publication: "));
  assert.ok(page.includes("Connection: "));
  assert.ok(page.includes("provider-account-application-card"));
  assert.ok(page.includes("Status"));
  assert.ok(page.includes("Current stage"));
  assert.ok(page.includes("Reference"));
  assert.ok(page.includes("Live profile"));
  assert.ok(page.includes("Active Profiles"));
  assert.ok(page.includes("Ready for Review"));
  assert.ok(page.includes("Under Platform Review"));
  assert.ok(page.includes("Published"));
  assert.ok(page.includes("Needs Attention"));
  assert.ok(page.includes("No actions currently require your attention."));
  assert.ok(page.includes("Recent activity"));
  assert.ok(page.includes("· Under Platform Review"));
  assert.ok(page.includes("Active session"));
  assert.ok(page.includes("Logout"));
  assert.ok(page.includes("Switch account"));
  assert.ok(page.includes("workItems"));
  assert.ok(page.includes("applicationAttentionItems"));
  assert.ok(page.includes("function applicationAttentionReason(application: ProviderWorkspaceApplication)"));
  assert.ok(page.includes("applicationLatestUpdateLabel(application)"));
  assert.ok(page.includes("applicationPrimaryActionHref(application)"));
  assert.ok(page.includes("applicationSecondaryActionHref(application)"));
  assert.ok(page.includes("isApplicationActive"));
  assert.ok(page.includes("isApplicationAttentionRequired"));
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
  assert.ok(page.includes('replace(":section", "overview")'));
  assert.ok(page.includes('application.status === "PUBLISHED"'));
  assert.ok(page.includes('return `${applicationStageLabel(application.currentStep)} · Published`;'));
  assert.ok(page.includes('return `${applicationStageLabel(application.currentStep)} · Under Platform Review`;'));
});

test("provider workspace surfaces unpublished public profiles with reason and review guidance", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  const api = read("src/api/providerAuth.ts");

  assert.ok(page.includes('action === "VIEW_UNPUBLISHED_PROFILE"'));
  assert.ok(page.includes('return profile.publicationReason || profile.attentionLabel || profile.nextActionLabel || null;'));
  assert.ok(page.includes('return `${versionLabel} · Unpublished`;'));
  assert.ok(page.includes("Review unpublished profile"));
  assert.ok(page.includes("entry.profile.nextActionLabel"));
  assert.ok(page.includes("publicationReason"));
  assert.ok(page.includes("Unpublished"));
  assert.ok(api.includes("publicationReason: string | null;"));
});

test("provider workspace continue-registration links use exact application references", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");

  assert.ok(page.includes("DISCOVER_ROUTES.providerApplicationDashboard.path"));
  assert.ok(page.includes('replace(":applicationReference", encodeURIComponent(application.referenceNumber))'));
  assert.ok(page.includes("applicationPrimaryActionHref(application)"));
  assert.ok(page.includes("applicationSecondaryActionHref(application)"));
  assert.ok(page.includes("Create another profile"));
  assert.ok(!page.includes("DISCOVER_ROUTES.providerDashboard.path"));
});
