import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("roles page groups permissions by module and labels pharmacy roles", () => {
  const source = readSource("pages/settings/UsersRolesPage.tsx");
  const entitlements = readSource("auth/moduleEntitlements.ts");
  assert.ok(source.includes("function groupPermissions("));
  assert.ok(source.includes("permissionModule(permission)"));
  assert.ok(source.includes("permissionLabel(permission)"));
  assert.ok(source.includes("PHARMACY_INVENTORY_MANAGER"));
  assert.ok(source.includes("PHARMACY_POS_USER"));
  assert.ok(source.includes("PHARMACIST"));
  assert.ok(entitlements.includes("PHARMACY_INVENTORY_MANAGER: \"Pharmacy Inventory Manager\""));
  assert.ok(entitlements.includes("PHARMACY_POS_USER: \"Pharmacy POS User\""));
  assert.ok(entitlements.includes("PHARMACIST: \"Pharmacist\""));
});
