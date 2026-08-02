import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("provider connections console is routed, gated, and linked from platform navigation", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const sidebar = readSource("layout/SidebarNav.tsx");
  const registry = readSource("modules/moduleRegistry.ts");
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  const api = readSource("api/clinicApi.ts");
  const permissions = readSource("auth/permissions.ts");

  assert.ok(app.includes('path="/platform/provider-connections/*"'));
  assert.ok(app.includes("ProviderConnectionsPage"));
  assert.ok(nav.includes('path: "/platform/provider-connections"'));
  assert.ok(nav.includes('label: "Provider Connections"'));
  assert.ok(sidebar.includes("platform-provider-connections"));
  assert.ok(registry.includes('path === "/platform/provider-connections"'));
  assert.ok(registry.includes('path.startsWith("/platform/provider-connections/")'));
  assert.ok(page.includes("Provider Connections"));
  assert.ok(page.includes("Public Profiles"));
  assert.ok(page.includes("Platform Entities"));
  assert.ok(page.includes("Suggested Matches"));
  assert.ok(page.includes("Conflicts"));
  assert.ok(page.includes("Audit"));
  assert.ok(page.includes("Reconcile"));
  assert.ok(page.includes("Review match"));
  assert.ok(page.includes("Reject suggestion"));
  assert.ok(page.includes("Propose link"));
  assert.ok(api.includes("getProviderConnectionsOverview"));
  assert.ok(api.includes("listProviderConnectionsPublicProfiles"));
  assert.ok(api.includes("listProviderConnectionsPublicPractices"));
  assert.ok(api.includes("listProviderConnectionsPlatformEntities"));
  assert.ok(api.includes("listProviderConnectionsLinks"));
  assert.ok(api.includes("listProviderConnectionsSuggestions"));
  assert.ok(api.includes("listProviderConnectionsConflicts"));
  assert.ok(api.includes("getProviderConnectionsAuditEvents"));
  assert.ok(api.includes("rejectProviderConnectionSuggestion"));
  assert.ok(api.includes("proposeProviderConnectionLink"));
  assert.ok(api.includes("approveProviderConnectionLink"));
  assert.ok(api.includes("activateProviderConnectionLink"));
  assert.ok(api.includes("unlinkProviderConnectionLink"));
  assert.ok(api.includes("relinkProviderConnectionLink"));
  assert.ok(api.includes("reconcileProviderConnection"));
  assert.ok(permissions.includes("platform.provider_connection.view"));
  assert.ok(permissions.includes("platform.provider_connection.propose"));
  assert.ok(permissions.includes("platform.provider_connection.approve"));
  assert.ok(permissions.includes("platform.provider_connection.unlink"));
  assert.ok(permissions.includes("platform.provider_connection.reconcile"));
  assert.ok(permissions.includes("platform.provider_connection.audit"));
});
