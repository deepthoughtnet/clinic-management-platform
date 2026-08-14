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

test("platform care access requests route and navigation are wired", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const sidebar = readSource("layout/SidebarNav.tsx");
  const page = readSource("pages/platform/CareAccessRequestsPage.tsx");
  const api = readSource("api/clinicApi.ts");

  assert.ok(app.includes('path="/platform/care-access-requests"'));
  assert.ok(app.includes("CareAccessRequestsPage"));
  assert.ok(nav.includes('key: "platform-care-access-requests"'));
  assert.ok(nav.includes("Care Access Requests"));
  assert.ok(nav.includes('path: "/platform/care-access-requests"'));
  assert.ok(sidebar.includes("platform-care-access-requests"));
  assert.ok(page.includes("Approve"));
  assert.ok(page.includes("Reject"));
  assert.ok(page.includes("Revoke"));
  assert.ok(page.includes("Requested"));
  assert.ok(page.includes("temporaryAccessCode"));
  assert.ok(page.includes("listCareAccessRequests"));
  assert.ok(page.includes("getCareAccessRequest"));
  assert.ok(page.includes("approveCareAccessRequest"));
  assert.ok(page.includes("rejectCareAccessRequest"));
  assert.ok(page.includes("revokeCareAccessRequest"));
  assert.ok(api.includes("listCareAccessRequests"));
  assert.ok(api.includes("approveCareAccessRequest"));
  assert.ok(api.includes("rejectCareAccessRequest"));
  assert.ok(api.includes("revokeCareAccessRequest"));
});
