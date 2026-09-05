import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readWebAdminSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("users roles page exposes edit modal for staff details", () => {
  const source = readWebAdminSource("pages/settings/UsersRolesPage.tsx");
  assert.ok(source.includes("Edit User"));
  assert.ok(source.includes("Save Changes"));
  assert.ok(source.includes("updateTenantUserProfile"));
  assert.ok(source.includes("edit-user-email"));
  assert.ok(source.includes("edit-user-username"));
  assert.ok(source.includes("mapIdentityConflicts"));
  assert.ok(source.includes("You can view users, but user management is restricted."));
  assert.ok(source.includes("canEditUser"));
});

test("users roles page keeps the staff table inside its own horizontal scroll container", () => {
  const source = readWebAdminSource("pages/settings/UsersRolesPage.tsx");
  assert.ok(source.includes("TableContainer"));
  assert.ok(source.includes('overflowX: "auto"'));
  assert.ok(source.includes('maxWidth: "100%"'));
  assert.ok(source.includes('minWidth: 0'));
  assert.ok(source.includes("minWidth: 1180"));
});

test("users roles page sanitizes mobile input to ten digits with numeric keyboard hints", () => {
  const source = readWebAdminSource("pages/settings/UsersRolesPage.tsx");
  assert.ok(source.includes("sanitizeIndianMobileInput(e.target.value)"));
  assert.ok(source.includes('inputMode: "numeric"'));
  assert.ok(source.includes("maxLength: 10"));
  assert.ok(source.includes("normalizeIndianMobileInput"));
});
