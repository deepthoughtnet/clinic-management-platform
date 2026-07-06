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
  assert.ok(source.includes("You can view users, but user management is restricted."));
  assert.ok(source.includes("canEditUser"));
});
