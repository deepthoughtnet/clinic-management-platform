import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("users roles page delegates permissions rendering to the new panel", () => {
  const source = readSource("pages/settings/UsersRolesPage.tsx");
  assert.ok(source.includes("RolesPermissionsPanel"));
  assert.ok(!source.includes("function groupPermissions("));
  assert.ok(!source.includes("function permissionLabel("));
  assert.ok(!source.includes("function permissionModule("));
});

test("roles permissions panel exposes business and technical views with filters", () => {
  const source = readSource("pages/settings/RolesPermissionsPanel.tsx");
  assert.ok(source.includes("Business View"));
  assert.ok(source.includes("Technical View"));
  assert.ok(source.includes("Search permissions"));
  assert.ok(source.includes("Module filter"));
  assert.ok(source.includes("Sensitive only"));
  assert.ok(source.includes("Expand All"));
  assert.ok(source.includes("Collapse All"));
  assert.ok(source.includes("Roles & Permissions"));
  assert.ok(source.includes("View effective system permissions for each tenant role. This page is read-only."));
  assert.ok(source.includes("Clinic Admin is the standard tenant-facing admin role. ADMIN and TENANT_ADMIN are compatibility roles retained for technical and legacy workflows."));
});
