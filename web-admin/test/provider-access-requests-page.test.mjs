import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? path.join(process.cwd(), "web-admin") : process.cwd();
}

function readSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "src", ...relPath.split("/")), "utf8");
}

test("platform provider access requests route and navigation are wired", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const sidebar = readSource("layout/SidebarNav.tsx");
  const page = readSource("pages/platform/ProviderAccessRequestsPage.tsx");
  const api = readSource("api/clinicApi.ts");

  assert.ok(app.includes('path="/platform/provider-access-requests"'));
  assert.ok(app.includes("ProviderAccessRequestsPage"));
  assert.ok(nav.includes('key: "platform-provider-access-requests"'));
  assert.ok(nav.includes("Provider Access Requests"));
  assert.ok(nav.includes('path: "/platform/provider-access-requests"'));
  assert.ok(sidebar.includes("platform-provider-access-requests"));
  assert.ok(page.includes("Approve"));
  assert.ok(page.includes("Reject"));
  assert.ok(page.includes("Revoke"));
  assert.ok(page.includes("Requested"));
  assert.ok(page.includes("temporaryAccessCode"));
  assert.ok(page.includes("listProviderAccessRequests"));
  assert.ok(page.includes("getProviderAccessRequest"));
  assert.ok(page.includes("approveProviderAccessRequest"));
  assert.ok(page.includes("rejectProviderAccessRequest"));
  assert.ok(page.includes("revokeProviderAccessRequest"));
  assert.ok(api.includes("listProviderAccessRequests"));
  assert.ok(api.includes("approveProviderAccessRequest"));
  assert.ok(api.includes("rejectProviderAccessRequest"));
  assert.ok(api.includes("revokeProviderAccessRequest"));
});
